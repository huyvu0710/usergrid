/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */
package org.apache.usergrid.persistence.index.impl;


import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang3.time.StopWatch;

import org.apache.usergrid.persistence.collection.util.EntityUtils;
import org.apache.usergrid.persistence.core.scope.ApplicationScope;
import org.apache.usergrid.persistence.core.scope.ApplicationScopeImpl;
import org.apache.usergrid.persistence.core.test.UseModules;
import org.apache.usergrid.persistence.core.util.Health;
import org.apache.usergrid.persistence.index.EntityIndex;
import org.apache.usergrid.persistence.index.EntityIndexBatch;
import org.apache.usergrid.persistence.index.EntityIndexFactory;
import org.apache.usergrid.persistence.index.IndexScope;
import org.apache.usergrid.persistence.index.SearchTypes;
import org.apache.usergrid.persistence.index.guice.TestIndexModule;
import org.apache.usergrid.persistence.index.query.CandidateResults;
import org.apache.usergrid.persistence.index.query.Query;
import org.apache.usergrid.persistence.index.utils.UUIDUtils;
import org.apache.usergrid.persistence.model.entity.Entity;
import org.apache.usergrid.persistence.model.entity.Id;
import org.apache.usergrid.persistence.model.entity.SimpleId;
import org.apache.usergrid.persistence.model.field.StringField;
import org.apache.usergrid.persistence.model.util.UUIDGenerator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;


@RunWith(EsRunner.class)
@UseModules({ TestIndexModule.class })
public class EntityIndexTest extends BaseIT {

    private static final Logger log = LoggerFactory.getLogger( EntityIndexTest.class );


    @Inject
    public EntityIndexFactory eif;



    @Test
    public void testIndex() throws IOException {
        Id appId = new SimpleId( "application" );

        ApplicationScope applicationScope = new ApplicationScopeImpl( appId );

        EntityIndex entityIndex = eif.createEntityIndex( applicationScope );
        entityIndex.initializeIndex();

        final String entityType = "thing";
        IndexScope indexScope = new IndexScopeImpl( appId, "things" );
        final SearchTypes searchTypes = SearchTypes.fromTypes( entityType );

        insertJsonBlob(entityIndex, entityType, indexScope, "/sample-large.json",100,0);

        entityIndex.refresh();

        testQueries( indexScope, searchTypes,  entityIndex );
    }

    @Test
    public void testMultipleIndexInitializations(){
        Id appId = new SimpleId( "application" );

        ApplicationScope applicationScope = new ApplicationScopeImpl( appId );

        EntityIndex entityIndex = eif.createEntityIndex( applicationScope );
        entityIndex.initializeIndex();
        for(int i=0;i<10;i++) {
            entityIndex.initializeIndex();
        }

    }

    @Test
    public void testAddMultipleIndexes() throws IOException {
        Id appId = new SimpleId( "application" );

        ApplicationScope applicationScope = new ApplicationScopeImpl( appId );

        EntityIndex entityIndex = eif.createEntityIndex( applicationScope );
        entityIndex.initializeIndex();

        final String entityType = "thing";
        IndexScope indexScope = new IndexScopeImpl( appId, "things" );
        final SearchTypes searchTypes = SearchTypes.fromTypes( entityType );

        insertJsonBlob(entityIndex, entityType, indexScope, "/sample-large.json",100,0);

        entityIndex.refresh();

        testQueries( indexScope, searchTypes,  entityIndex );

        HashMap<String,Object> config = new HashMap<>();
        config.put("replicas",0);
        config.put("shards",1);

        entityIndex.addIndex("v2", config);

        insertJsonBlob(entityIndex, entityType, indexScope, "/sample-large.json",100,100);

        entityIndex.refresh();

        //Hilda Youn
        testQuery(indexScope, searchTypes, entityIndex, "name = 'Hilda Young'", 1 );

        testQuery(indexScope, searchTypes, entityIndex, "name = 'Lowe Kelley'", 1 );
    }

    private void insertJsonBlob(EntityIndex entityIndex, String entityType, IndexScope indexScope, String filePath,final int max,final int startIndex) throws IOException {
        InputStream is = this.getClass().getResourceAsStream( filePath );
        ObjectMapper mapper = new ObjectMapper();
        List<Object> sampleJson = mapper.readValue( is, new TypeReference<List<Object>>() {} );

        int count = 0;
        StopWatch timer = new StopWatch();
        timer.start();

        final EntityIndexBatch batch = entityIndex.createBatch();

        if(startIndex > 0){
            for(int i =0; i<startIndex;i++){
                sampleJson.remove(0);
            }
        }

        for ( Object o : sampleJson ) {

            Map<String, Object> item = ( Map<String, Object> ) o;

            Entity entity = new Entity( entityType );
            entity = EntityIndexMapUtils.fromMap(entity, item);
            EntityUtils.setVersion(entity, UUIDGenerator.newTimeUUID());

            batch.index( indexScope, entity );

            if(count %1000 == 0){
                batch.execute();
            }



            if ( count++ > max ) {
                break;
            }
        }

        batch.execute();
        timer.stop();
        log.info( "Total time to index {} entries {}ms, average {}ms/entry",
                new Object[] { count, timer.getTime(), timer.getTime() / count } );
    }


    @Test
    public void testDeindex() {

        Id appId = new SimpleId( "application" );

        ApplicationScope applicationScope = new ApplicationScopeImpl( appId );

        IndexScope indexScope = new IndexScopeImpl( appId, "fastcars" );

        EntityIndex entityIndex = eif.createEntityIndex( applicationScope );
        entityIndex.initializeIndex();

        Map entityMap = new HashMap() {{
            put( "name", "Ferrari 212 Inter" );
            put( "introduced", 1952 );
            put( "topspeed", 215 );
        }};


        Entity entity = EntityIndexMapUtils.fromMap( entityMap );
        EntityUtils.setId( entity, new SimpleId( "fastcar" ) );
        EntityUtils.setVersion( entity, UUIDGenerator.newTimeUUID() );
        entityIndex.createBatch().index(indexScope , entity ).executeAndRefresh();

        CandidateResults candidateResults = entityIndex.search( indexScope, SearchTypes.fromTypes(entity.getId().getType()),
                Query.fromQL( "name contains 'Ferrari*'" ) );
        assertEquals( 1, candidateResults.size() );

        entityIndex.createBatch().deindex( indexScope, entity ).execute();

        entityIndex.refresh();

        candidateResults = entityIndex.search( indexScope, SearchTypes.fromTypes(entity.getId().getType()), Query.fromQL( "name contains 'Ferrari*'" ) );
        assertEquals( 0, candidateResults.size() );
    }


    private void testQuery(final IndexScope scope, final SearchTypes searchTypes, final EntityIndex entityIndex, final String queryString, final int num ) {

        StopWatch timer = new StopWatch();
        timer.start();
        Query query = Query.fromQL( queryString );
        query.setLimit( 1000 );
        CandidateResults candidateResults = entityIndex.search( scope, searchTypes, query );
        timer.stop();

        assertEquals( num, candidateResults.size() );
        log.debug( "Query time {}ms", timer.getTime() );
    }


    private void testQueries(final IndexScope scope, SearchTypes searchTypes, final EntityIndex entityIndex ) {


        testQuery(scope, searchTypes, entityIndex, "name = 'Morgan Pierce'", 1 );

        testQuery(scope, searchTypes, entityIndex, "name = 'morgan pierce'", 1 );

        testQuery(scope, searchTypes, entityIndex, "name = 'Morgan'", 0 );

        testQuery(scope, searchTypes, entityIndex, "name contains 'Morgan'", 1 );

        testQuery(scope, searchTypes, entityIndex, "company > 'GeoLogix'", 64 );

        testQuery(scope, searchTypes, entityIndex, "gender = 'female'", 45 );

        testQuery(scope, searchTypes, entityIndex, "name = 'Minerva Harrell' and age > 39", 1 );

        testQuery(scope, searchTypes, entityIndex, "name = 'Minerva Harrell' and age > 39 and age < 41", 1 );

        testQuery(scope, searchTypes, entityIndex, "name = 'Minerva Harrell' and age > 40", 0 );

        testQuery(scope, searchTypes, entityIndex, "name = 'Minerva Harrell' and age >= 40", 1 );

        testQuery(scope, searchTypes, entityIndex, "name = 'Minerva Harrell' and age <= 40", 1 );

        testQuery(scope, searchTypes, entityIndex, "name = 'Morgan* '", 1 );

        testQuery(scope, searchTypes, entityIndex, "name = 'Morgan*'", 1 );


        // test a couple of array sub-property queries

        int totalUsers = 102;

        // nobody has a friend named Jack the Ripper
        testQuery(scope, searchTypes, entityIndex, "friends.name = 'Jack the Ripper'", 0 );

        // everybody doesn't have a friend named Jack the Ripper
        testQuery(scope,  searchTypes,entityIndex, "not (friends.name = 'Jack the Ripper')", totalUsers );

        // one person has a friend named Shari Hahn
        testQuery(scope, searchTypes, entityIndex, "friends.name = 'Wendy Moody'", 1 );

        // everybody but 1 doesn't have a friend named Shari Hahh
        testQuery(scope, searchTypes, entityIndex, "not (friends.name = 'Shari Hahn')", totalUsers - 1);

    }


    /**
     * Tests that Entity-to-map and Map-to-entity round trip works.
     */
    @Test
    public void testEntityIndexMapUtils() throws IOException {

        InputStream is = this.getClass().getResourceAsStream( "/sample-small.json" );
        ObjectMapper mapper = new ObjectMapper();
        List<Object> contacts = mapper.readValue( is, new TypeReference<List<Object>>() {} );

        for ( Object o : contacts ) {

            Map<String, Object> map1 = ( Map<String, Object> ) o;

            // convert map to entity
            Entity entity1 = EntityIndexMapUtils.fromMap( map1 );

            // convert entity back to map
            Map map2 = EntityIndexMapUtils.toMap( entity1 );

            // the two maps should be the same
            Map diff = Maps.difference( map1, map2 ).entriesDiffering();
            assertEquals( 0, diff.size() );
        }
    }


    @Test
    public void getEntityVersions() throws Exception {

        Id appId = new SimpleId( "application" );
        Id ownerId = new SimpleId( "owner" );

        ApplicationScope applicationScope = new ApplicationScopeImpl( appId );

        IndexScope indexScope = new IndexScopeImpl( ownerId, "users" );



        EntityIndex entityIndex = eif.createEntityIndex( applicationScope );
        entityIndex.initializeIndex();

        final String middleName = "middleName" + UUIDUtils.newTimeUUID();
        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put( "username", "edanuff" );
        properties.put( "email", "ed@anuff.com" );
        properties.put( "middlename", middleName );

        Map entityMap = new HashMap() {{
            put( "username", "edanuff" );
            put( "email", "ed@anuff.com" );
            put( "middlename", middleName );
        }};

        final Id userId = new SimpleId("user");

        Entity user = EntityIndexMapUtils.fromMap( entityMap );
        EntityUtils.setId( user, userId);
        EntityUtils.setVersion( user, UUIDGenerator.newTimeUUID() );


        final EntityIndexBatch batch = entityIndex.createBatch();

        batch.index( indexScope, user );

        user.setField( new StringField( "address1", "1782 address st" ) );
        batch.index( indexScope, user );
        user.setField( new StringField( "address2", "apt 508" ) );
        batch.index( indexScope,  user );
        user.setField( new StringField( "address3", "apt 508" ) );
        batch.index( indexScope,  user );
        batch.executeAndRefresh();

        CandidateResults results = entityIndex.getEntityVersions(indexScope,  user.getId() );

        assertEquals(1,  results.size());
        assertEquals( results.get( 0 ).getId(), user.getId() );
        assertEquals( results.get(0).getVersion(), user.getVersion());
    }


    @Test
    public void deleteVerification() throws Throwable {

        Id appId = new SimpleId( "application" );
        Id ownerId = new SimpleId( "owner" );

        ApplicationScope applicationScope = new ApplicationScopeImpl( appId );

        IndexScope appScope = new IndexScopeImpl( ownerId, "user" );

        EntityIndex ei = eif.createEntityIndex( applicationScope );
        ei.initializeIndex();

        final String middleName = "middleName" + UUIDUtils.newTimeUUID();

        Map entityMap = new HashMap() {{
            put( "username", "edanuff" );
            put( "email", "ed@anuff.com" );
            put( "middlename", middleName );
        }};

        Entity user = EntityIndexMapUtils.fromMap( entityMap );
        EntityUtils.setId( user, new SimpleId( "edanuff" ) );
        EntityUtils.setVersion( user, UUIDGenerator.newTimeUUID() );


        EntityIndexBatch batch = ei.createBatch();

        batch.index( appScope, user ).executeAndRefresh();
        Query query = new Query();
        query.addEqualityFilter( "username", "edanuff" );
        CandidateResults r = ei.search( appScope, SearchTypes.fromTypes( "edanuff" ), query );
        assertEquals( user.getId(), r.get( 0 ).getId() );

        batch.deindex(appScope, user.getId(), user.getVersion() ).executeAndRefresh();


        // EntityRef
        query = new Query();
        query.addEqualityFilter( "username", "edanuff" );
        r = ei.search(appScope,SearchTypes.fromTypes( "edanuff" ),  query );

        assertFalse( r.iterator().hasNext() );
    }

    @Test
    public void multiValuedTypes() {

        Id appId = new SimpleId( "entityindextest" );
        Id ownerId = new SimpleId( "multivaluedtype" );
        ApplicationScope applicationScope = new ApplicationScopeImpl( appId );

        IndexScope appScope = new IndexScopeImpl( ownerId, "user" );

        EntityIndex ei = eif.createEntityIndex( applicationScope );
        ei.initializeIndex();
        ei.createBatch();

        // Bill has favorites as string, age as string and retirement goal as number
        Map billMap = new HashMap() {{
            put( "username", "bill" );
            put( "email", "bill@example.com" );
            put( "age", "thirtysomething");
            put( "favorites", "scallops, croquet, wine");
            put( "retirementGoal", 100000);
        }};
        Entity bill = EntityIndexMapUtils.fromMap( billMap );
        EntityUtils.setId( bill, new SimpleId( UUIDGenerator.newTimeUUID(), "user"  ) );
        EntityUtils.setVersion( bill, UUIDGenerator.newTimeUUID() );

        EntityIndexBatch batch = ei.createBatch();

        batch.index( appScope,  bill );

        // Fred has age as int, favorites as object and retirement goal as object
        Map fredMap = new HashMap() {{
            put( "username", "fred" );
            put( "email", "fred@example.com" );
            put( "age", 41 );
            put( "favorites", new HashMap<String, Object>() {{
                put("food", "cheezewiz");
                put("sport", "nascar");
                put("beer", "budwizer");
            }});
            put( "retirementGoal", new HashMap<String, Object>() {{
                put("car", "Firebird");
                put("home", "Mobile");
            }});
        }};
        Entity fred = EntityIndexMapUtils.fromMap( fredMap );
        EntityUtils.setId( fred, new SimpleId( UUIDGenerator.newTimeUUID(), "user"  ) );
        EntityUtils.setVersion( fred, UUIDGenerator.newTimeUUID() );
        batch.index( appScope, fred );

        batch.executeAndRefresh();

        final SearchTypes searchTypes = SearchTypes.fromTypes( "user" );

        Query query = new Query();
        query.addEqualityFilter( "username", "bill" );
        CandidateResults r = ei.search( appScope, searchTypes,  query );
        assertEquals( bill.getId(), r.get( 0 ).getId() );

        query = new Query();
        query.addEqualityFilter( "username", "fred" );
        r = ei.search( appScope, searchTypes,  query );
        assertEquals( fred.getId(), r.get( 0 ).getId() );

        query = new Query();
        query.addEqualityFilter( "age", 41 );
        r = ei.search( appScope, searchTypes,  query );
        assertEquals( fred.getId(), r.get( 0 ).getId() );

        query = new Query();
        query.addEqualityFilter( "age", "thirtysomething" );
        r = ei.search(  appScope, searchTypes, query );
        assertEquals( bill.getId(), r.get( 0 ).getId() );
    }


    @Test
    public void healthTest() {

        Id appId = new SimpleId( "application" );
        ApplicationScope applicationScope = new ApplicationScopeImpl( appId );

        EntityIndex ei = eif.createEntityIndex( applicationScope );

        assertNotEquals( "cluster should be ok", Health.RED, ei.getClusterHealth() );
        assertEquals( "index not be ready yet", Health.RED, ei.getIndexHealth() );

        ei.initializeIndex();
        ei.refresh();

        assertNotEquals( "cluster should be fine", Health.RED, ei.getIndexHealth() );
        assertNotEquals( "cluster should be ready now", Health.RED, ei.getClusterHealth() );
    }
}


