/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.graph.internal.concept;

import ai.grakn.Grakn;
import ai.grakn.GraknTx;
import ai.grakn.GraknTxType;
import ai.grakn.concept.Concept;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Entity;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.Relationship;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Resource;
import ai.grakn.concept.ResourceType;
import ai.grakn.concept.Role;
import ai.grakn.concept.Thing;
import ai.grakn.exception.GraphOperationException;
import ai.grakn.exception.InvalidGraphException;
import ai.grakn.graph.internal.GraknTxAbstract;
import ai.grakn.graph.internal.GraphTestBase;
import ai.grakn.util.ErrorMessage;
import ai.grakn.util.Schema;
import com.google.common.collect.Iterables;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class RelationshipTest extends GraphTestBase {
    private RelationshipImpl relation;
    private RoleImpl role1;
    private ThingImpl rolePlayer1;
    private RoleImpl role2;
    private ThingImpl rolePlayer2;
    private RoleImpl role3;
    private EntityType type;
    private RelationshipType relationshipType;

    @Before
    public void buildGraph(){
        role1 = (RoleImpl) graknGraph.putRole("Role 1");
        role2 = (RoleImpl) graknGraph.putRole("Role 2");
        role3 = (RoleImpl) graknGraph.putRole("Role 3");

        type = graknGraph.putEntityType("Main concept Type").plays(role1).plays(role2).plays(role3);
        relationshipType = graknGraph.putRelationshipType("Main relation type").relates(role1).relates(role2).relates(role3);

        rolePlayer1 = (ThingImpl) type.addEntity();
        rolePlayer2 = (ThingImpl) type.addEntity();

        relation = (RelationshipImpl) relationshipType.addRelationship();

        relation.addRolePlayer(role1, rolePlayer1);
        relation.addRolePlayer(role2, rolePlayer2);
    }

    @Test
    public void whenAddingRolePlayerToRelation_RelationIsExpanded(){
        Relationship relationship = relationshipType.addRelationship();
        Role role = graknGraph.putRole("A role");
        Entity entity1 = type.addEntity();

        relationship.addRolePlayer(role, entity1);
        assertThat(relationship.allRolePlayers().keySet(), containsInAnyOrder(role1, role2, role3, role));
        assertThat(relationship.allRolePlayers().get(role), containsInAnyOrder(entity1));
    }

    @Test
    public void checkShortcutEdgesAreCreatedBetweenAllRolePlayers(){
        //Create the Ontology
        Role role1 = graknGraph.putRole("Role 1");
        Role role2 = graknGraph.putRole("Role 2");
        Role role3 = graknGraph.putRole("Role 3");
        RelationshipType relType = graknGraph.putRelationshipType("Rel Type").relates(role1).relates(role2).relates(role3);
        EntityType entType = graknGraph.putEntityType("Entity Type").plays(role1).plays(role2).plays(role3);

        //Data
        EntityImpl entity1r1 = (EntityImpl) entType.addEntity();
        EntityImpl entity2r1 = (EntityImpl) entType.addEntity();
        EntityImpl entity3r2r3 = (EntityImpl) entType.addEntity();
        EntityImpl entity4r3 = (EntityImpl) entType.addEntity();
        EntityImpl entity5r1 = (EntityImpl) entType.addEntity();
        EntityImpl entity6r1r2r3 = (EntityImpl) entType.addEntity();

        //Relationship
        Relationship relationship = relationshipType.addRelationship();
        relationship.addRolePlayer(role1, entity1r1);
        relationship.addRolePlayer(role1, entity2r1);
        relationship.addRolePlayer(role1, entity5r1);
        relationship.addRolePlayer(role1, entity6r1r2r3);
        relationship.addRolePlayer(role2, entity3r2r3);
        relationship.addRolePlayer(role2, entity6r1r2r3);
        relationship.addRolePlayer(role3, entity3r2r3);
        relationship.addRolePlayer(role3, entity4r3);
        relationship.addRolePlayer(role3, entity6r1r2r3);

        //Check the structure of the NEW shortcut edges
        assertThat(followShortcutsToNeighbours(graknGraph, entity1r1),
                containsInAnyOrder(entity1r1, entity2r1, entity3r2r3, entity4r3, entity5r1, entity6r1r2r3));
        assertThat(followShortcutsToNeighbours(graknGraph, entity2r1),
                containsInAnyOrder(entity2r1, entity1r1, entity3r2r3, entity4r3, entity5r1, entity6r1r2r3));
        assertThat(followShortcutsToNeighbours(graknGraph, entity3r2r3),
                containsInAnyOrder(entity1r1, entity2r1, entity3r2r3, entity4r3, entity5r1, entity6r1r2r3));
        assertThat(followShortcutsToNeighbours(graknGraph, entity4r3),
                containsInAnyOrder(entity1r1, entity2r1, entity3r2r3, entity4r3, entity5r1, entity6r1r2r3));
        assertThat(followShortcutsToNeighbours(graknGraph, entity5r1),
                containsInAnyOrder(entity1r1, entity2r1, entity3r2r3, entity4r3, entity5r1, entity6r1r2r3));
        assertThat(followShortcutsToNeighbours(graknGraph, entity6r1r2r3),
                containsInAnyOrder(entity1r1, entity2r1, entity3r2r3, entity4r3, entity5r1, entity6r1r2r3));
    }
    private Set<Concept> followShortcutsToNeighbours(GraknTx graph, Thing thing) {
        List<Vertex> vertices = graph.admin().getTinkerTraversal().V().has(Schema.VertexProperty.ID.name(), thing.getId().getValue()).
                in(Schema.EdgeLabel.SHORTCUT.getLabel()).
                out(Schema.EdgeLabel.SHORTCUT.getLabel()).toList();

        return vertices.stream().map(vertex -> graph.admin().buildConcept(vertex).asThing()).collect(Collectors.toSet());
    }

    @Test
    public void whenGettingRolePlayersOfRelation_ReturnsRolesAndInstances() throws Exception {
        assertThat(relation.allRolePlayers().keySet(), containsInAnyOrder(role1, role2, role3));
        assertThat(relation.rolePlayers(role1).collect(toSet()), containsInAnyOrder(rolePlayer1));
        assertThat(relation.rolePlayers(role2).collect(toSet()), containsInAnyOrder(rolePlayer2));
    }

    @Test
    public void whenCreatingRelation_EnsureUniqueHashIsCreatedBasedOnRolePlayers() throws InvalidGraphException {
        Role role1 = graknGraph.putRole("role type 1");
        Role role2 = graknGraph.putRole("role type 2");
        EntityType type = graknGraph.putEntityType("concept type").plays(role1).plays(role2);
        RelationshipType relationshipType = graknGraph.putRelationshipType("relation type").relates(role1).relates(role2);

        relationshipType.addRelationship();
        graknGraph.commit();
        graknGraph = (GraknTxAbstract<?>) Grakn.session(Grakn.IN_MEMORY, graknGraph.getKeyspace()).open(GraknTxType.WRITE);

        relation = (RelationshipImpl) graknGraph.getRelationshipType("relation type").instances().iterator().next();

        role1 = graknGraph.putRole("role type 1");
        Thing thing1 = type.addEntity();

        TreeMap<Role, Thing> roleMap = new TreeMap<>();
        roleMap.put(role1, thing1);
        roleMap.put(role2, null);

        relation.addRolePlayer(role1, thing1);

        graknGraph.commit();
        graknGraph = (GraknTxAbstract<?>) Grakn.session(Grakn.IN_MEMORY, graknGraph.getKeyspace()).open(GraknTxType.WRITE);

        relation = (RelationshipImpl) graknGraph.getRelationshipType("relation type").instances().iterator().next();
        assertEquals(getFakeId(relation.type(), roleMap), relation.reified().get().getIndex());
    }
    private String getFakeId(RelationshipType relationshipType, TreeMap<Role, Thing> roleMap){
        String itemIdentifier = "RelationType_" + relationshipType.getId() + "_Relation";
        for(Map.Entry<Role, Thing> entry: roleMap.entrySet()){
            itemIdentifier = itemIdentifier + "_" + entry.getKey().getId();
            if(entry.getValue() != null) itemIdentifier += "_" + entry.getValue().getId();
        }
        return itemIdentifier;
    }

    @Test
    public void whenAddingDuplicateRelations_Throw() throws InvalidGraphException {
        Role role1 = graknGraph.putRole("role type 1");
        Role role2 = graknGraph.putRole("role type 2");
        EntityType type = graknGraph.putEntityType("concept type").plays(role1).plays(role2);
        RelationshipType relationshipType = graknGraph.putRelationshipType("My relation type").relates(role1).relates(role2);
        Thing thing1 = type.addEntity();
        Thing thing2 = type.addEntity();

        relationshipType.addRelationship().addRolePlayer(role1, thing1).addRolePlayer(role2, thing2);
        relationshipType.addRelationship().addRolePlayer(role1, thing1).addRolePlayer(role2, thing2);

        expectedException.expect(InvalidGraphException.class);
        expectedException.expectMessage(containsString("You have created one or more relations"));

        graknGraph.commit();
    }

    @Test
    public void ensureRelationToStringContainsRolePlayerInformation(){
        Role role1 = graknGraph.putRole("role type 1");
        Role role2 = graknGraph.putRole("role type 2");
        RelationshipType relationshipType = graknGraph.putRelationshipType("A relationship Type").relates(role1).relates(role2);
        EntityType type = graknGraph.putEntityType("concept type").plays(role1).plays(role2);
        Thing thing1 = type.addEntity();
        Thing thing2 = type.addEntity();

        Relationship relationship = relationshipType.addRelationship().addRolePlayer(role1, thing1).addRolePlayer(role2, thing2);

        String mainDescription = "ID [" + relationship.getId() +  "] Type [" + relationship.type().getLabel() + "] Roles and Role Players:";
        String rolerp1 = "    Role [" + role1.getLabel() + "] played by [" + thing1.getId() + ",]";
        String rolerp2 = "    Role [" + role2.getLabel() + "] played by [" + thing2.getId() + ",]";

        assertTrue("Relationship toString missing main description", relationship.toString().contains(mainDescription));
        assertTrue("Relationship toString missing role and role player definition", relationship.toString().contains(rolerp1));
        assertTrue("Relationship toString missing role and role player definition", relationship.toString().contains(rolerp2));
    }

    @Test
    public void whenDeletingRelations_EnsureCastingsRemain(){
        Role entityRole = graknGraph.putRole("Entity Role");
        Role degreeRole = graknGraph.putRole("Degree Role");
        EntityType entityType = graknGraph.putEntityType("Entity Type").plays(entityRole);
        ResourceType<Long> degreeType = graknGraph.putResourceType("Resource Type", ResourceType.DataType.LONG).plays(degreeRole);

        RelationshipType hasDegree = graknGraph.putRelationshipType("Has Degree").relates(entityRole).relates(degreeRole);

        Entity entity = entityType.addEntity();
        Resource<Long> degree1 = degreeType.putResource(100L);
        Resource<Long> degree2 = degreeType.putResource(101L);

        Relationship relationship1 = hasDegree.addRelationship().addRolePlayer(entityRole, entity).addRolePlayer(degreeRole, degree1);
        hasDegree.addRelationship().addRolePlayer(entityRole, entity).addRolePlayer(degreeRole, degree2);

        assertEquals(2, entity.relations().count());

        relationship1.delete();

        assertEquals(1, entity.relations().count());
    }


    @Test
    public void whenDeletingFinalInstanceOfRelation_RelationIsDeleted(){
        Role roleA = graknGraph.putRole("RoleA");
        Role roleB = graknGraph.putRole("RoleB");
        Role roleC = graknGraph.putRole("RoleC");

        RelationshipType relation = graknGraph.putRelationshipType("relation type").relates(roleA).relates(roleB).relates(roleC);
        EntityType type = graknGraph.putEntityType("concept type").plays(roleA).plays(roleB).plays(roleC);
        Entity a = type.addEntity();
        Entity b = type.addEntity();
        Entity c = type.addEntity();

        ConceptId relationId = relation.addRelationship().addRolePlayer(roleA, a).addRolePlayer(roleB, b).addRolePlayer(roleC, c).getId();

        a.delete();
        assertNotNull(graknGraph.getConcept(relationId));
        b.delete();
        assertNotNull(graknGraph.getConcept(relationId));
        c.delete();
        assertNull(graknGraph.getConcept(relationId));
    }

    @Test
    public void whenAddingNullRolePlayerToRelation_Throw(){
        expectedException.expect(NullPointerException.class);
        relationshipType.addRelationship().addRolePlayer(null, rolePlayer1);
    }

    @Test
    public void whenAttemptingToLinkTheInstanceOfAResourceRelationToTheResourceWhichCreatedIt_ThrowIfTheRelationTypeDoesNotHavePermissionToPlayTheNecessaryRole(){
        ResourceType<String> resourceType = graknGraph.putResourceType("what a pain", ResourceType.DataType.STRING);
        Resource<String> resource = resourceType.putResource("a real pain");

        EntityType entityType = graknGraph.putEntityType("yay").resource(resourceType);
        Relationship implicitRelationship = Iterables.getOnlyElement(entityType.addEntity().resource(resource).relations().collect(Collectors.toSet()));

        expectedException.expect(GraphOperationException.class);
        expectedException.expectMessage(GraphOperationException.hasNotAllowed(implicitRelationship, resource).getMessage());

        implicitRelationship.resource(resource);
    }


    @Test
    public void whenAddingDuplicateRelationsWithDifferentKeys_EnsureTheyCanBeCommitted(){
        Role role1 = graknGraph.putRole("dark");
        Role role2 = graknGraph.putRole("souls");
        ResourceType<Long> resourceType = graknGraph.putResourceType("Death Number", ResourceType.DataType.LONG);
        RelationshipType relationshipType = graknGraph.putRelationshipType("Dark Souls").relates(role1).relates(role2).key(resourceType);
        EntityType entityType = graknGraph.putEntityType("Dead Guys").plays(role1).plays(role2);

        Entity e1 = entityType.addEntity();
        Entity e2 = entityType.addEntity();

        Resource<Long> r1 = resourceType.putResource(1000000L);
        Resource<Long> r2 = resourceType.putResource(2000000L);

        Relationship rel1 = relationshipType.addRelationship().addRolePlayer(role1, e1).addRolePlayer(role2, e2);
        Relationship rel2 = relationshipType.addRelationship().addRolePlayer(role1, e1).addRolePlayer(role2, e2);

        //Set the keys and commit. Without this step it should fail
        rel1.resource(r1);
        rel2.resource(r2);

        graknGraph.commit();
        graknGraph = (GraknTxAbstract<?>) graknSession.open(GraknTxType.WRITE);

        assertThat(graknGraph.admin().getMetaRelationType().instances().collect(toSet()), Matchers.hasItem(rel1));
        assertThat(graknGraph.admin().getMetaRelationType().instances().collect(toSet()), Matchers.hasItem(rel2));
    }

    @Test
    public void whenAddingDuplicateRelationsWithSameKeys_Throw(){
        Role role1 = graknGraph.putRole("dark");
        Role role2 = graknGraph.putRole("souls");
        ResourceType<Long> resourceType = graknGraph.putResourceType("Death Number", ResourceType.DataType.LONG);
        RelationshipType relationshipType = graknGraph.putRelationshipType("Dark Souls").relates(role1).relates(role2).key(resourceType);
        EntityType entityType = graknGraph.putEntityType("Dead Guys").plays(role1).plays(role2);

        Entity e1 = entityType.addEntity();
        Entity e2 = entityType.addEntity();

        Resource<Long> r1 = resourceType.putResource(1000000L);

        relationshipType.addRelationship().addRolePlayer(role1, e1).addRolePlayer(role2, e2).resource(r1);
        relationshipType.addRelationship().addRolePlayer(role1, e1).addRolePlayer(role2, e2).resource(r1);

        String message = ErrorMessage.VALIDATION_RELATION_DUPLICATE.getMessage("");
        message = message.substring(0, message.length() - 5);
        expectedException.expect(InvalidGraphException.class);
        expectedException.expectMessage(containsString(message));

        graknGraph.commit();
    }
}