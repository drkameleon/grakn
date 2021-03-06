/*
 * Copyright (C) 2021 Grakn Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package grakn.core.query;

import grabl.tracing.client.GrablTracingThreadStatic.ThreadTrace;
import grakn.core.common.exception.GraknException;
import grakn.core.concept.ConceptManager;
import grakn.core.concept.type.AttributeType;
import grakn.core.concept.type.AttributeType.ValueType;
import grakn.core.concept.type.EntityType;
import grakn.core.concept.type.RelationType;
import grakn.core.concept.type.RoleType;
import grakn.core.concept.type.ThingType;
import grakn.core.logic.LogicManager;
import grakn.core.pattern.constraint.type.LabelConstraint;
import grakn.core.pattern.constraint.type.OwnsConstraint;
import grakn.core.pattern.constraint.type.PlaysConstraint;
import grakn.core.pattern.constraint.type.RegexConstraint;
import grakn.core.pattern.constraint.type.RelatesConstraint;
import grakn.core.pattern.constraint.type.SubConstraint;
import grakn.core.pattern.variable.TypeVariable;
import grakn.core.pattern.variable.VariableRegistry;
import graql.lang.pattern.schema.Rule;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static grabl.tracing.client.GrablTracingThreadStatic.traceOnThread;
import static grakn.core.common.exception.ErrorMessage.TypeRead.ROLE_TYPE_SCOPE_IS_NOT_RELATION_TYPE;
import static grakn.core.common.exception.ErrorMessage.TypeRead.TYPE_NOT_FOUND;
import static grakn.core.common.exception.ErrorMessage.TypeWrite.ATTRIBUTE_VALUE_TYPE_DEFINED_NOT_ON_ATTRIBUTE_TYPE;
import static grakn.core.common.exception.ErrorMessage.TypeWrite.ATTRIBUTE_VALUE_TYPE_MISSING;
import static grakn.core.common.exception.ErrorMessage.TypeWrite.ATTRIBUTE_VALUE_TYPE_MODIFIED;
import static grakn.core.common.exception.ErrorMessage.TypeWrite.CYCLIC_TYPE_HIERARCHY;
import static grakn.core.common.exception.ErrorMessage.TypeWrite.INVALID_DEFINE_SUB;
import static grakn.core.common.exception.ErrorMessage.TypeWrite.ROLE_DEFINED_OUTSIDE_OF_RELATION;
import static grakn.core.common.exception.ErrorMessage.TypeWrite.TYPE_CONSTRAINT_UNACCEPTED;
import static graql.lang.common.GraqlToken.Constraint.IS;

public class Definer {

    private static final String TRACE_PREFIX = "definer.";

    private final LogicManager logicMgr;
    private final ConceptManager conceptMgr;
    private final Set<TypeVariable> created;
    private final Set<TypeVariable> variables;
    private final List<graql.lang.pattern.schema.Rule> rules;

    private Definer(ConceptManager conceptMgr, LogicManager logicMgr, Set<TypeVariable> variables, List<Rule> rules) {
        this.logicMgr = logicMgr;
        this.conceptMgr = conceptMgr;
        this.variables = variables;
        this.rules = rules;
        this.created = new HashSet<>();
    }

    public static Definer create(ConceptManager conceptMgr, LogicManager logicMgr,
                                 List<graql.lang.pattern.variable.TypeVariable> variables,
                                 List<Rule> rules) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "create")) {
            return new Definer(conceptMgr, logicMgr, VariableRegistry.createFromTypes(variables).types(), rules);
        }
    }

    public void execute() {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "execute")) {
            validateTypeHierarchyIsNotCyclic(variables);
            variables.forEach(variable -> {
                if (!created.contains(variable)) define(variable);
            });
            rules.forEach(this::define);
        }
    }

    private ThingType define(TypeVariable variable) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "define")) {
            assert variable.label().isPresent();
            final LabelConstraint labelConstraint = variable.label().get();

            if (labelConstraint.scope().isPresent() && variable.constraints().size() > 1) {
                throw GraknException.of(ROLE_DEFINED_OUTSIDE_OF_RELATION, labelConstraint.scopedLabel());
            } else if (!variable.is().isEmpty()) {
                throw GraknException.of(TYPE_CONSTRAINT_UNACCEPTED, IS);
            } else if (labelConstraint.scope().isPresent()) return null; // do nothing
            else if (created.contains(variable)) return conceptMgr.getThingType(labelConstraint.scopedLabel());

            ThingType type = getThingType(labelConstraint);
            if (variable.sub().isPresent()) {
                type = defineSub(type, variable.sub().get(), variable);
            } else if (variable.valueType().isPresent()) { // && variable.sub().size() == 0
                throw GraknException.of(ATTRIBUTE_VALUE_TYPE_MODIFIED,
                                        variable.valueType().get().valueType().name(),
                                        labelConstraint.label());
            } else if (type == null) {
                throw GraknException.of(TYPE_NOT_FOUND, labelConstraint.label());
            }

            if (variable.valueType().isPresent() && !(type instanceof AttributeType)) {
                throw GraknException.of(ATTRIBUTE_VALUE_TYPE_DEFINED_NOT_ON_ATTRIBUTE_TYPE, labelConstraint.label());
            }

            created.add(variable);

            if (variable.abstractConstraint().isPresent()) defineAbstract(type);
            if (variable.regex().isPresent()) defineRegex(type.asAttributeType().asString(), variable.regex().get());

            if (!variable.relates().isEmpty()) defineRelates(type.asRelationType(), variable.relates());
            if (!variable.owns().isEmpty()) defineOwns(type, variable.owns());
            if (!variable.plays().isEmpty()) definePlays(type, variable.plays());

            return type;
        }
    }

    private void validateTypeHierarchyIsNotCyclic(Set<TypeVariable> variables) {
        final Set<TypeVariable> visited = new HashSet<>();
        for (TypeVariable variable : variables) {
            if (visited.contains(variable)) continue;
            assert variable.label().isPresent();
            final LinkedHashSet<String> hierarchy = new LinkedHashSet<>();
            hierarchy.add(variable.label().get().scopedLabel());
            visited.add(variable);
            while (variable.sub().isPresent()) {
                variable = variable.sub().get().type();
                assert variable.label().isPresent();
                if (!hierarchy.add(variable.label().get().scopedLabel())) {
                    throw GraknException.of(CYCLIC_TYPE_HIERARCHY, hierarchy);
                }
            }
        }
    }

    private ThingType getThingType(LabelConstraint label) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "getthingtype")) {
            final ThingType thingType;
            if ((thingType = conceptMgr.getThingType(label.label())) != null) return thingType;
            else return null;
        }
    }

    private RoleType getRoleType(LabelConstraint label) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "getroletype")) {
            // We always assume that Role Types already exist,
            // defined by their Relation Types ahead of time
            assert label.scope().isPresent();
            final ThingType thingType;
            final RoleType roleType;
            if ((thingType = conceptMgr.getThingType(label.scope().get())) == null) {
                throw GraknException.of(TYPE_NOT_FOUND, label.scope().get());
            } else if (!thingType.isRelationType()) {
                throw GraknException.of(ROLE_TYPE_SCOPE_IS_NOT_RELATION_TYPE, label.scopedLabel(), label.scope().get());
            } else if ((roleType = thingType.asRelationType().getRelates(label.label())) == null) {
                throw GraknException.of(TYPE_NOT_FOUND, label.scopedLabel());
            }
            return roleType;
        }
    }

    private ThingType defineSub(ThingType thingType, SubConstraint subConstraint, TypeVariable var) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "definesub")) {
            final LabelConstraint labelConstraint = var.label().get();
            final ThingType supertype = define(subConstraint.type()).asThingType();
            if (supertype instanceof EntityType) {
                if (thingType == null) thingType = conceptMgr.putEntityType(labelConstraint.label());
                thingType.asEntityType().setSupertype(supertype.asEntityType());
            } else if (supertype instanceof RelationType) {
                if (thingType == null) thingType = conceptMgr.putRelationType(labelConstraint.label());
                thingType.asRelationType().setSupertype(supertype.asRelationType());
            } else if (supertype instanceof AttributeType) {
                final ValueType valueType;
                if (var.valueType().isPresent()) valueType = ValueType.of(var.valueType().get().valueType());
                else if (!supertype.isRoot()) valueType = supertype.asAttributeType().getValueType();
                else throw GraknException.of(ATTRIBUTE_VALUE_TYPE_MISSING, labelConstraint.label());
                if (thingType == null) thingType = conceptMgr.putAttributeType(labelConstraint.label(), valueType);
                thingType.asAttributeType().setSupertype(supertype.asAttributeType());
            } else {
                throw GraknException.of(INVALID_DEFINE_SUB, labelConstraint.scopedLabel(), supertype.getLabel());
            }
            return thingType;
        }
    }

    private void defineAbstract(ThingType thingType) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "defineabstract")) {
            thingType.setAbstract();
        }
    }

    private void defineRegex(AttributeType.String attributeType, RegexConstraint regexConstraint) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "defineregex")) {
            attributeType.setRegex(regexConstraint.regex());
        }
    }

    private void defineRelates(RelationType relationType, Set<RelatesConstraint> relatesConstraints) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "definerelates")) {
            relatesConstraints.forEach(relates -> {
                final String roleTypeLabel = relates.role().label().get().label();
                if (relates.overridden().isPresent()) {
                    final String overriddenTypeLabel = relates.overridden().get().label().get().label();
                    relationType.setRelates(roleTypeLabel, overriddenTypeLabel);
                    created.add(relates.overridden().get());
                } else {
                    relationType.setRelates(roleTypeLabel);
                }
                created.add(relates.role());
            });
        }
    }

    private void defineOwns(ThingType thingType, Set<OwnsConstraint> ownsConstraints) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "defineowns")) {
            ownsConstraints.forEach(owns -> {
                final AttributeType attributeType = define(owns.attribute()).asAttributeType();
                if (owns.overridden().isPresent()) {
                    final AttributeType overriddenType = define(owns.overridden().get()).asAttributeType();
                    thingType.setOwns(attributeType, overriddenType, owns.isKey());
                } else {
                    thingType.setOwns(attributeType, owns.isKey());
                }
            });
        }
    }

    private void definePlays(ThingType thingType, Set<PlaysConstraint> playsConstraints) {
        try (ThreadTrace ignored = traceOnThread(TRACE_PREFIX + "defineplays")) {
            playsConstraints.forEach(plays -> {
                define(plays.relation().get());
                final RoleType roleType = getRoleType(plays.role().label().get()).asRoleType();
                if (plays.overridden().isPresent()) {
                    final RoleType overriddenType = getRoleType(plays.overridden().get().label().get()).asRoleType();
                    thingType.setPlays(roleType, overriddenType);
                } else {
                    thingType.setPlays(roleType);
                }
            });
        }
    }

    private void define(graql.lang.pattern.schema.Rule rule) {
        logicMgr.putRule(rule.label(), rule.when(), rule.then());
    }
}
