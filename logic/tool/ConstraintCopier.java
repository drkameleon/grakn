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
 */

package grakn.core.logic.tool;

import grakn.core.common.exception.GraknException;
import grakn.core.pattern.constraint.thing.HasConstraint;
import grakn.core.pattern.constraint.thing.IsaConstraint;
import grakn.core.pattern.constraint.thing.RelationConstraint;
import grakn.core.pattern.constraint.thing.ValueConstraint;
import grakn.core.pattern.constraint.type.SubConstraint;
import grakn.core.pattern.variable.ThingVariable;
import grakn.core.pattern.variable.TypeVariable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static grakn.core.common.exception.ErrorMessage.Internal.ILLEGAL_STATE;
import static grakn.core.common.iterator.Iterators.iterate;

public class ConstraintCopier {
    public static RelationConstraint copyConstraint(RelationConstraint relationConstraint) {
        ThingVariable ownerCopy = copyIsaAndValues(relationConstraint.owner());
        LinkedHashSet<RelationConstraint.RolePlayer> rolePlayersCopy = copyRolePlayers(relationConstraint.players());
        return new RelationConstraint(ownerCopy, rolePlayersCopy);
    }

    static LinkedHashSet<RelationConstraint.RolePlayer> copyRolePlayers(LinkedHashSet<RelationConstraint.RolePlayer> players) {
        return iterate(players).map(rolePlayer -> {
            TypeVariable roleTypeCopy = rolePlayer.roleType().isPresent() ? copyVariableWithLabelAndValueType(rolePlayer.roleType().get()) : null;
            ThingVariable playerCopy = copyIsaAndValues(rolePlayer.player());
            return new RelationConstraint.RolePlayer(roleTypeCopy, playerCopy, rolePlayer.repetition());
        }).toLinkedSet();
    }

    public static HasConstraint copyConstraint(HasConstraint hasConstraint) {
        ThingVariable ownerCopy = copyIsaAndValues(hasConstraint.owner());
        ThingVariable attributeCopy = copyIsaAndValues(hasConstraint.attribute());
        return ownerCopy.has(attributeCopy);
    }

    // NOTE: copies resolved types as well
    public static IsaConstraint copyConstraint(IsaConstraint isa) {
        ThingVariable newOwner = ThingVariable.of(isa.owner().id());
        newOwner.addResolvedTypes(isa.owner().resolvedTypes());
        copyValuesOntoVariable(isa.owner().value(), newOwner);
        return copyIsaOntoVariable(isa, newOwner);
    }

    public static ValueConstraint<?> copyConstraint(ValueConstraint<?> value) {
        //NOTE: isa can never exist on a Value Concludable (or else it would be a Isa Concludable).
        ThingVariable newOwner = ThingVariable.of(value.owner().id());
        newOwner.addResolvedTypes(value.owner().resolvedTypes());
        Set<ValueConstraint<?>> otherValues = value.owner().value().stream()
                .filter(value1 -> !value.equals(value1)).collect(Collectors.toSet());
        copyValuesOntoVariable(otherValues, newOwner);
        return copyValueOntoVariable(value, newOwner);
    }

    static IsaConstraint copyIsaOntoVariable(IsaConstraint toCopy, ThingVariable variableToConstrain) {
        TypeVariable typeCopy = copyVariableWithLabelAndValueType(toCopy.type());
        IsaConstraint newIsa = variableToConstrain.isa(typeCopy, toCopy.isExplicit());
        return newIsa;
    }

    static void copyValuesOntoVariable(Set<ValueConstraint<?>> toCopy, ThingVariable newOwner) {
        toCopy.forEach(valueConstraint -> copyValueOntoVariable(valueConstraint, newOwner));
    }

    static ValueConstraint<?> copyValueOntoVariable(ValueConstraint<?> toCopy, ThingVariable toConstrain) {
        if (toCopy.isLong())
            return toConstrain.valueLong(toCopy.asLong().predicate(), toCopy.asLong().value());
        else if (toCopy.isDouble())
            return toConstrain.valueDouble(toCopy.asDouble().predicate(), toCopy.asDouble().value());
        else if (toCopy.isBoolean())
            return toConstrain.valueBoolean(toCopy.asBoolean().predicate(), toCopy.asBoolean().value());
        else if (toCopy.isString())
            return toConstrain.valueString(toCopy.asString().predicate(), toCopy.asString().value());
        else if (toCopy.isDateTime())
            return toConstrain.valueDateTime(toCopy.asDateTime().predicate(), toCopy.asDateTime().value());
        else if (toCopy.isVariable()) {
            ThingVariable copyOfVar = copyIsaAndValues(toCopy.asVariable().value());
            return toConstrain.valueVariable(toCopy.asValue().predicate().asEquality(), copyOfVar);
        } else throw GraknException.of(ILLEGAL_STATE);
    }

    static ThingVariable copyIsaAndValues(ThingVariable copyFrom) {
        ThingVariable copy = ThingVariable.of(copyFrom.id());
        copy.addResolvedTypes(copyFrom.resolvedTypes());
        copyIsaAndValues(copyFrom, copy);
        return copy;
    }

    public static void copyIsaAndValues(ThingVariable oldOwner, ThingVariable newOwner) {
        if (oldOwner.isa().isPresent()) copyIsaOntoVariable(oldOwner.isa().get(), newOwner);
        copyValuesOntoVariable(oldOwner.value(), newOwner);
    }

    public static void copyLabelSubAndValueType(TypeVariable copyFrom, TypeVariable copyTo) {
        if (copyFrom.label().isPresent()) copyTo.label(copyFrom.label().get().properLabel());
        if (copyFrom.sub().isPresent()) {
            SubConstraint subCopy = copyFrom.sub().get();
            copyTo.sub(subCopy.type(), subCopy.isExplicit());
        }
        if (copyFrom.valueType().isPresent()) copyTo.valueType(copyFrom.valueType().get().valueType());
    }

    static TypeVariable copyVariableWithLabelAndValueType(TypeVariable copyFrom) {
        TypeVariable copy = TypeVariable.of(copyFrom.id());
        copy.addResolvedTypes(copyFrom.resolvedTypes());
        copyLabelSubAndValueType(copyFrom, copy);
        return copy;
    }
}
