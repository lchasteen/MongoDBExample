/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb;

import category.Slow;
import org.bson.BSONObject;
import org.bson.BsonBinarySubType;
import org.bson.BsonBinaryWriter;
import org.bson.BsonObjectId;
import org.bson.Document;
import org.bson.io.OutputBuffer;
import org.bson.types.BSONTimestamp;
import org.bson.types.Binary;
import org.bson.types.Code;
import org.bson.types.CodeWScope;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.mongodb.ClusterFixture.disableMaxTimeFailPoint;
import static com.mongodb.ClusterFixture.enableMaxTimeFailPoint;
import static com.mongodb.ClusterFixture.isDiscoverableReplicaSet;
import static com.mongodb.ClusterFixture.isSharded;
import static com.mongodb.ClusterFixture.serverVersionAtLeast;
import static com.mongodb.DBObjectMatchers.hasSubdocument;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

public class DBCollectionTest extends DatabaseTestCase {

    @Test
    public void shouldCreateIdOnInsertIfThereIsNone() {
        BasicDBObject document = new BasicDBObject();
        collection.insert(document);
        assertEquals(ObjectId.class, document.get("_id").getClass());
        assertEquals(document, collection.findOne());
    }

    @Test
    public void shouldCreateIdOnInsertIfTheValueIsNull() {
        BasicDBObject document = new BasicDBObject("_id", null);
        collection.insert(document);
        assertEquals(ObjectId.class, document.get("_id").getClass());
        assertEquals(document, collection.findOne());
    }

    @Test
    public void saveShouldInsertADocumentWithNullId() {
        BasicDBObject document = new BasicDBObject("_id", null);
        collection.save(document);
        assertEquals(ObjectId.class, document.get("_id").getClass());
        assertEquals(document, collection.findOne());
    }

    @Test
    public void saveShouldInsertADocumentWithNewObjectId() {
        ObjectId newObjectId = new ObjectId();
        BasicDBObject document = new BasicDBObject("_id", newObjectId);
        collection.save(document);
        assertEquals(newObjectId, document.get("_id"));
        assertEquals(document, collection.findOne());
    }

    @Test
    public void testDefaultSettings() {
        assertNull(collection.getDBDecoderFactory());
        assertNull(collection.getDBEncoderFactory());
        assertEquals(BasicDBObject.class, collection.getObjectClass());
        assertNull(collection.getHintFields());
        assertEquals(ReadPreference.primary(), collection.getReadPreference());
        assertEquals(WriteConcern.ACKNOWLEDGED, collection.getWriteConcern());
        assertEquals(0, collection.getOptions());
    }

    @Test
    public void testInsert() {
        WriteResult res = collection.insert(new BasicDBObject("_id", 1).append("x", 2));
        assertNotNull(res);
        assertEquals(1L, collection.count());
        assertEquals(new BasicDBObject("_id", 1).append("x", 2), collection.findOne());
    }

    @Test
    public void testInsertDuplicateKeyException() {
        DBObject doc = new BasicDBObject("_id", 1);
        collection.insert(doc, WriteConcern.ACKNOWLEDGED);
        try {
            collection.insert(doc, WriteConcern.ACKNOWLEDGED);
            fail("should throw DuplicateKey exception");
        } catch (DuplicateKeyException e) {
            assertThat(e.getCode(), is(11000));
        }
    }

    @Test
    public void testSaveDuplicateKeyException() {
        collection.createIndex(new BasicDBObject("x", 1), new BasicDBObject("unique", true));
        collection.save(new BasicDBObject("x", 1), WriteConcern.ACKNOWLEDGED);
        try {
            collection.save(new BasicDBObject("x", 1), WriteConcern.ACKNOWLEDGED);
            fail("should throw DuplicateKey exception");
        } catch (DuplicateKeyException e) {
            assertThat(e.getCode(), is(11000));
        }
    }

    @Test
    public void testSaveWithIdDefined() {
        DBObject document = new BasicDBObject("_id", new ObjectId()).append("a", Math.random());
        collection.save(document);
        assertThat(collection.count(), is(1L));
        assertEquals(document, collection.findOne());
    }

    @Test
    public void testUpdate() {
        WriteResult res = collection.update(new BasicDBObject("_id", 1),
                                            new BasicDBObject("$set", new BasicDBObject("x", 2)),
                                            true, false);
        assertNotNull(res);
        assertEquals(1L, collection.count());
        assertEquals(new BasicDBObject("_id", 1).append("x", 2), collection.findOne());
    }

    @Test
    public void testObjectClass() {
        collection.setObjectClass(MyDBObject.class);
        collection.insert(new BasicDBObject("_id", 1));
        DBObject obj = collection.findOne();
        assertEquals(MyDBObject.class, obj.getClass());
    }

    @Test
    public void testDotInDBObject() {
        try {
            collection.save(new BasicDBObject("x.y", 1));
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // all good
        }

        try {
            collection.save(new BasicDBObject("x", new BasicDBObject("a.b", 1)));
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // all good
        }

        try {
            Map<String, Integer> map = new HashMap<String, Integer>();
            map.put("a.b", 1);
            collection.save(new BasicDBObject("x", map));
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // all good
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testJAVA794() {
        Map<String, String> nested = new HashMap<String, String>();
        nested.put("my.dot.field", "foo");
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        list.add(nested);
        collection.save(new BasicDBObject("_document_", new BasicDBObject("array", list)));
    }

    @Test
    public void testInsertWithDBEncoder() {
        List<DBObject> objects = new ArrayList<DBObject>();
        objects.add(new BasicDBObject("a", 1));
        collection.insert(objects, WriteConcern.ACKNOWLEDGED, new MyEncoder());
        assertEquals(MyEncoder.getConstantObject(), collection.findOne());
    }

    @Test
    public void testInsertWithDBEncoderFactorySet() {
        collection.setDBEncoderFactory(new MyEncoderFactory());
        List<DBObject> objects = new ArrayList<DBObject>();
        objects.add(new BasicDBObject("a", 1));
        collection.insert(objects, WriteConcern.ACKNOWLEDGED, null);
        assertEquals(MyEncoder.getConstantObject(), collection.findOne());
        collection.setDBEncoderFactory(null);
    }

    @Test(expected = MongoCommandException.class)
    public void testCreateIndexWithInvalidIndexType() {
        assumeThat(serverVersionAtLeast(asList(2, 6, 0)), is(true));
        DBObject index = new BasicDBObject("x", "funny");
        collection.createIndex(index);
    }

    @Test
    public void testCreateIndexByName() {
        collection.createIndex("x");
        assertEquals(2, collection.getIndexInfo().size());

        assertNotNull(getIndexInfoForNameStartingWith("x"));
    }

    @Test
    public void testCreateIndexAsAscending() {
        collection.createIndex(new BasicDBObject("x", 1));
        assertEquals(2, collection.getIndexInfo().size());

        assertNotNull(getIndexInfoForNameStartingWith("x_1"));
    }

    @Test
    public void testCreateIndexAsDescending() {
        DBObject index = new BasicDBObject("x", -1);
        collection.createIndex(index);

        DBObject indexInfo = getIndexInfoForNameStartingWith("x_-1");
        assertEquals(indexInfo.get("key"), index);
    }

    @Test
    public void testCreateIndexByKeysName() {
        collection.createIndex(new BasicDBObject("x", 1), "zulu");
        assertEquals(2, collection.getIndexInfo().size());
        DBObject indexInfo = getIndexInfoForNameStartingWith("zulu");

        assertEquals("zulu", indexInfo.get("name"));
    }

    @Test
    public void testCreateIndexByKeysNameUnique() {
        collection.createIndex(new BasicDBObject("x", 1), "zulu", true);
        assertEquals(2, collection.getIndexInfo().size());
        DBObject indexInfo = getIndexInfoForNameStartingWith("zulu");

        assertEquals("zulu", indexInfo.get("name"));
        assertTrue((Boolean) indexInfo.get("unique"));
    }

    @Test
    public void testCreateIndexAs2d() {
        DBObject index = new BasicDBObject("x", "2d");
        collection.createIndex(index);

        DBObject indexInfo = getIndexInfoForNameStartingWith("x");
        assertEquals(indexInfo.get("key"), index);
    }

    @Test
    public void testCreateIndexAs2dsphere() {
        assumeThat(serverVersionAtLeast(asList(2, 4, 0)), is(true));

        // when
        DBObject index = new BasicDBObject("x", "2dsphere");
        collection.createIndex(index);

        // then
        DBObject indexInfo = getIndexInfoForNameStartingWith("x");
        assertEquals(indexInfo.get("key"), index);
    }

    @Test
    public void testCreateIndexAsText() {
        assumeThat(serverVersionAtLeast(asList(2, 6, 0)), is(true));
        DBObject index = new BasicDBObject("x", "text");
        collection.createIndex(index);

        DBObject indexInfo = getIndexInfoForNameStartingWith("x");
        assertEquals(indexInfo.get("name"), "x_text");
        assertThat(indexInfo.get("weights"), notNullValue());
    }

    @Test
    public void testRemoveWithDBEncoder() {
        DBObject document = new BasicDBObject("x", 1);
        collection.insert(document);
        collection.insert(MyEncoder.getConstantObject());
        collection.remove(new BasicDBObject("x", 1), WriteConcern.ACKNOWLEDGED, new MyEncoder());

        assertEquals(1, collection.count());
        assertEquals(document, collection.findOne());
    }

    @Test
    public void testCount() {
        for (int i = 0; i < 10; i++) {
            collection.insert(new BasicDBObject("_id", i));
        }
        assertEquals(10, collection.getCount());
        assertEquals(5, collection.getCount(new BasicDBObject("_id", new BasicDBObject("$lt", 5))));
        assertEquals(4, collection.getCount(new BasicDBObject("_id", new BasicDBObject("$lt", 5)), null, 100, 1));
        assertEquals(4, collection.getCount(new BasicDBObject("_id", new BasicDBObject("$lt", 5)), null, 4, 0));
    }

    @Test
    @Category(Slow.class)
    public void testUpdateWithDBEncoder() {
        DBObject document = new BasicDBObject("_id", 1).append("x", 1);
        collection.insert(document);
        collection.update(new BasicDBObject("x", 1),
                          new BasicDBObject("y", false),
                          true,
                          false,
                          WriteConcern.ACKNOWLEDGED,
                          new MyEncoder());

        assertEquals(1, collection.count());
        assertThat(collection.find(), hasItem(MyEncoder.getConstantObject()));
    }

    @Test
    public void testSaveWithDBEncoder() {
        try {
            DBObject document = new BasicDBObject("_id", 1).append("x", 1);
            collection.setDBEncoderFactory(new MyEncoderFactory());
            collection.save(document);

            assertEquals(1, collection.count());
            assertThat(collection.find(), hasItem(MyEncoder.getConstantObject()));

            collection.save(document);

            assertEquals(1, collection.count());
            assertThat(collection.find(), hasItem(MyEncoder.getConstantObject()));
        } finally {
            collection.setDBEncoderFactory(null);
        }
    }

    @Test
    public void testFindAndRemove() {
        DBObject doc = new BasicDBObject("_id", 1).append("x", true);
        collection.insert(doc);
        DBObject newDoc = collection.findAndRemove(new BasicDBObject("_id", 1));
        assertEquals(doc, newDoc);
        assertEquals(0, collection.count());
    }

    @Test
    public void testFindAndReplace() {
        DBObject doc1 = new BasicDBObject("_id", 1).append("x", true);
        DBObject doc2 = new BasicDBObject("_id", 1).append("y", false);

        collection.insert(doc1);
        DBObject newDoc = collection.findAndModify(new BasicDBObject("x", true), doc2);
        assertEquals(doc1, newDoc);
        assertEquals(doc2, collection.findOne());
    }

    @Test
    public void testFindAndReplaceOrInsert() {
        collection.insert(new BasicDBObject("_id", 1).append("p", "abc"));

        DBObject doc = new BasicDBObject("_id", 2).append("p", "foo");

        DBObject newDoc = collection.findAndModify(new BasicDBObject("p", "bar"),
                                                   null,
                                                   null,
                                                   false,
                                                   doc,
                                                   false,
                                                   true);
        assertNull(newDoc);
        assertEquals(doc, collection.findOne(null, null, new BasicDBObject("_id", -1)));
    }

    @Test
    public void testFindAndUpdate() {
        collection.insert(new BasicDBObject("_id", 1).append("x", true));
        DBObject newDoc = collection.findAndModify(new BasicDBObject("x", true),
                                                   new BasicDBObject("$set", new BasicDBObject("x", false)));
        assertNotNull(newDoc);
        assertEquals(new BasicDBObject("_id", 1).append("x", true), newDoc);
        assertEquals(new BasicDBObject("_id", 1).append("x", false), collection.findOne());
    }

    @Test
    public void findAndUpdateAndReturnNew() {
        DBObject newDoc = collection.findAndModify(new BasicDBObject("x", true),
                                                   null,
                                                   null,
                                                   false,
                                                   new BasicDBObject("$set", new BasicDBObject("x", false)),
                                                   true,
                                                   true);
        assertNotNull(newDoc);
        assertThat(newDoc, hasSubdocument(new BasicDBObject("x", false)));
    }

    @Test(expected = MongoExecutionTimeoutException.class)
    public void testFindAndUpdateTimeout() {
        assumeThat(ClusterFixture.isAuthenticated(), is(false));
        assumeThat(serverVersionAtLeast(asList(2, 6, 0)), is(true));
        collection.insert(new BasicDBObject("_id", 1));
        enableMaxTimeFailPoint();
        try {
            collection.findAndModify(new BasicDBObject("_id", 1), null, null, false, new BasicDBObject("$set", new BasicDBObject("x", 1)),
                                     false, false, 1, TimeUnit.SECONDS);
        } finally {
            disableMaxTimeFailPoint();
        }
    }

    @Test(expected = MongoExecutionTimeoutException.class)
    public void testFindAndReplaceTimeout() {
        assumeThat(isSharded(), is(false));
        assumeThat(serverVersionAtLeast(asList(2, 6, 0)), is(true));
        collection.insert(new BasicDBObject("_id", 1));
        enableMaxTimeFailPoint();
        try {
            collection.findAndModify(new BasicDBObject("_id", 1), null, null, false, new BasicDBObject("x", 1), false, false,
                                     1, TimeUnit.SECONDS);
        } finally {
            disableMaxTimeFailPoint();
        }
    }

    @Test(expected = MongoExecutionTimeoutException.class)
    public void testFindAndRemoveTimeout() {
        assumeThat(isSharded(), is(false));
        assumeThat(serverVersionAtLeast(asList(2, 6, 0)), is(true));
        collection.insert(new BasicDBObject("_id", 1));
        enableMaxTimeFailPoint();
        try {
            collection.findAndModify(new BasicDBObject("_id", 1), null, null, true, null, false, false, 1, TimeUnit.SECONDS);
        } finally {
            disableMaxTimeFailPoint();
        }
    }

    @Test
    @Category(Slow.class)
    public void testFindAndReplaceA16MDocument() {
        BasicDBObject documentWithJustId = new BasicDBObject("_id", 42);
        DBObject foundDocument = collection.findAndModify(documentWithJustId, new BasicDBObject("_id", 1), null, false,
                                                          new BasicDBObject("_id", 42).append("b", new byte[16 * 1024 * 1024 - 30]), true,
                                                          true);
        assertEquals(documentWithJustId, foundDocument);
    }

    @Test
    public void testGenericBinary() {
        byte[] data = {1, 2, 3};
        collection.insert(new BasicDBObject("binary", new Binary(data)));
        assertArrayEquals(data, (byte[]) collection.findOne().get("binary"));
    }

    @Test
    public void testOtherBinary() {
        byte[] data = {1, 2, 3};
        Binary binaryValue = new Binary(BsonBinarySubType.USER_DEFINED, data);
        collection.insert(new BasicDBObject("binary", binaryValue));
        assertEquals(binaryValue, collection.findOne().get("binary"));
    }

    @Test
    public void testUUID() {
        UUID uuid = UUID.randomUUID();
        collection.insert(new BasicDBObject("uuid", uuid));
        assertEquals(uuid, collection.findOne().get("uuid"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDotKeysArrayFail() {
        //JAVA-794
        DBObject obj = new BasicDBObject("x", 1).append("y", 2)
                                                .append("array", new Object[]{new BasicDBObject("foo.bar", "baz")});
        collection.insert(obj);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDotKeysListFail() {
        //JAVA-794
        DBObject obj = new BasicDBObject("x", 1).append("y", 2)
                                                .append("array", asList(new BasicDBObject("foo.bar", "baz")));
        collection.insert(obj);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDotKeysMapInArrayFail() {
        final Map<String, Object> map = new HashMap<String, Object>(1);
        map.put("foo.bar", 2);
        DBObject obj = new BasicDBObject("x", 1).append("y", 2).append("array", new Object[]{map});
        collection.insert(obj);
    }

    @Test
    public void testPathToClassMapDecoding() {
        collection.setObjectClass(TopLevelDBObject.class);
        collection.setInternalClass("a", NestedOneDBObject.class);
        collection.setInternalClass("a.b", NestedTwoDBObject.class);

        DBObject doc = new TopLevelDBObject().append("a", new NestedOneDBObject().append("b", asList(new NestedTwoDBObject()))
                                                                                 .append("c", new BasicDBObject()));
        collection.save(doc);
        assertEquals(doc, collection.findOne());
    }


    @Test
    public void testReflectionObject() {
        collection.setObjectClass(Tweet.class);
        Tweet insertedTweet = new Tweet(1, "Lorem", new Date(12));
        collection.insert(insertedTweet);

        assertThat(collection.count(), is(1L));

        DBObject document = collection.findOne();
        assertThat(document, instanceOf(Tweet.class));
        Tweet tweet = (Tweet) document;

        assertThat(tweet.getUserId(), is(1L));
        assertThat(tweet.getMessage(), is("Lorem"));
        assertThat(tweet.getDate(), is(new Date(12)));

        insertedTweet.setMessage("Lorem 2");

        DBObject replacedTweet = collection.findAndModify(new BasicDBObject(), insertedTweet);
        assertThat(replacedTweet, instanceOf(Tweet.class));
        tweet = (Tweet) replacedTweet;

        assertThat(tweet.getUserId(), is(1L));
        assertThat(tweet.getMessage(), is("Lorem"));
        assertThat(tweet.getDate(), is(new Date(12)));

        DBObject removedTweet = collection.findAndRemove(new BasicDBObject());
        assertThat(removedTweet, instanceOf(Tweet.class));
        tweet = (Tweet) removedTweet;

        assertThat(tweet.getUserId(), is(1L));
        assertThat(tweet.getMessage(), is("Lorem 2"));
        assertThat(tweet.getDate(), is(new Date(12)));
    }

    @Test
    public void testReflectionObjectAtLevel2() {
        collection.insert(new BasicDBObject("t", new Tweet(1, "Lorem", new Date(12))));
        collection.setInternalClass("t", Tweet.class);

        DBObject document = collection.findOne();
        assertThat(document.get("t"), instanceOf(Tweet.class));

        Tweet tweet = (Tweet) document.get("t");

        assertThat(tweet.getUserId(), is(1L));
        assertThat(tweet.getMessage(), is("Lorem"));
        assertThat(tweet.getDate(), is(new Date(12)));
    }

    @Test
    public void shouldAcceptDocumentsWithAllValidValueTypes() {
        BasicDBObject doc = new BasicDBObject();
        doc.append("_id", new ObjectId());
        doc.append("bool", true);
        doc.append("int", 3);
        doc.append("short", (short) 4);
        doc.append("long", 5L);
        doc.append("str", "Hello MongoDB");
        doc.append("float", 6.0f);
        doc.append("double", 1.1);
        doc.append("date", new Date());
        doc.append("ts", new BSONTimestamp(5, 1));
        doc.append("pattern", Pattern.compile(".*"));
        doc.append("minKey", new MinKey());
        doc.append("maxKey", new MaxKey());
        doc.append("js", new Code("code"));
        doc.append("jsWithScope", new CodeWScope("code", new BasicDBObject()));
        doc.append("null", null);
        doc.append("uuid", UUID.randomUUID());
        doc.append("db ref", new com.mongodb.DBRef("test", new ObjectId()));
        doc.append("binary", new Binary((byte) 42, new byte[]{10, 11, 12}));
        doc.append("byte array", new byte[]{1, 2, 3});
        doc.append("int array", new int[]{4, 5, 6});
        doc.append("list", asList(7, 8, 9));
        doc.append("doc list", asList(new Document("x", 1), new Document("x", 2)));

        collection.insert(doc);
        DBObject found = collection.findOne();
        assertNotNull(found);
        assertEquals(ObjectId.class, found.get("_id").getClass());
        assertEquals(Boolean.class, found.get("bool").getClass());
        assertEquals(Integer.class, found.get("int").getClass());
        assertEquals(Integer.class, found.get("short").getClass());
        assertEquals(Long.class, found.get("long").getClass());
        assertEquals(String.class, found.get("str").getClass());
        assertEquals(Double.class, found.get("float").getClass());
        assertEquals(Double.class, found.get("double").getClass());
        assertEquals(Date.class, found.get("date").getClass());
        assertEquals(BSONTimestamp.class, found.get("ts").getClass());
        assertEquals(Pattern.class, found.get("pattern").getClass());
        assertEquals(MinKey.class, found.get("minKey").getClass());
        assertEquals(MaxKey.class, found.get("maxKey").getClass());
        assertEquals(Code.class, found.get("js").getClass());
        assertEquals(CodeWScope.class, found.get("jsWithScope").getClass());
        assertNull(found.get("null"));
        assertEquals(UUID.class, found.get("uuid").getClass());
        assertEquals(DBRef.class, found.get("db ref").getClass());
        assertEquals(Binary.class, found.get("binary").getClass());
        assertEquals(byte[].class, found.get("byte array").getClass());
        assertTrue(found.get("int array") instanceof List);
        assertTrue(found.get("list") instanceof List);
        assertTrue(found.get("doc list") instanceof List);
    }


    @Test
    public void testCompoundCodecWithDefaultValues() {
        assertThat(collection.getObjectCodec(), instanceOf(CompoundDBObjectCodec.class));
        CompoundDBObjectCodec codec = (CompoundDBObjectCodec) collection.getObjectCodec();
        assertThat(codec.getDecoder(), instanceOf(DBObjectCodec.class));
        assertThat(codec.getEncoder(), instanceOf(DBObjectCodec.class));
    }

    @Test
    public void testCompoundCodecWithCustomEncoderFactory() {
        collection.setDBEncoderFactory(new DBEncoderFactory() {
            @Override
            public DBEncoder create() {
                return new DefaultDBEncoder();
            }
        });
        assertThat(collection.getObjectCodec(), instanceOf(CompoundDBObjectCodec.class));
        CompoundDBObjectCodec codec = (CompoundDBObjectCodec) collection.getObjectCodec();
        assertThat(codec.getEncoder(), instanceOf(DBEncoderFactoryAdapter.class));
    }

    @Test
    public void testCompoundCodecWithCustomDecoderFactory() {
        collection.setDBDecoderFactory(new DBDecoderFactory() {
            @Override
            public DBDecoder create() {
                return new DefaultDBDecoder();
            }
        });
        assertThat(collection.getObjectCodec(), instanceOf(CompoundDBObjectCodec.class));
        CompoundDBObjectCodec codec = (CompoundDBObjectCodec) collection.getObjectCodec();
        assertThat(codec.getDecoder(), instanceOf(DBDecoderAdapter.class));
    }

    @Test
    public void testBulkWriteOperation() {
        // given
        collection.insert(Arrays.<DBObject>asList(new BasicDBObject("_id", 3),
                                                  new BasicDBObject("_id", 4),
                                                  new BasicDBObject("_id", 5),
                                                  new BasicDBObject("_id", 6).append("z", 1),
                                                  new BasicDBObject("_id", 7).append("z", 1),
                                                  new BasicDBObject("_id", 8).append("z", 2),
                                                  new BasicDBObject("_id", 9).append("z", 2)));

        // when
        BulkWriteOperation bulkWriteOperation = collection.initializeOrderedBulkOperation();
        bulkWriteOperation.insert(new BasicDBObject("_id", 0));
        ObjectId upsertOneId = new ObjectId();
        ObjectId upsertTwoId = new ObjectId();
        bulkWriteOperation.find(new BasicDBObject("_id", upsertOneId)).upsert()
                          .updateOne(new BasicDBObject("$set", new BasicDBObject("x", 2)));
        bulkWriteOperation.find(new BasicDBObject("_id", upsertTwoId)).upsert()
                          .replaceOne(new BasicDBObject("_id", upsertTwoId).append("y", 2));
        bulkWriteOperation.find(new BasicDBObject("_id", 3)).removeOne();
        bulkWriteOperation.find(new BasicDBObject("_id", 4)).updateOne(new BasicDBObject("$set", new BasicDBObject("x", 1)));
        bulkWriteOperation.find(new BasicDBObject("_id", 5)).replaceOne(new BasicDBObject("_id", 5).append("y", 1));
        bulkWriteOperation.find(new BasicDBObject("z", 1)).remove();
        bulkWriteOperation.find(new BasicDBObject("z", 2)).update(new BasicDBObject("$set", new BasicDBObject("z", 3)));

        BulkWriteResult result = bulkWriteOperation.execute();

        // then
        assertTrue(bulkWriteOperation.isOrdered());
        assertTrue(result.isAcknowledged());
        assertEquals(1, result.getInsertedCount());
        assertEquals(4, result.getMatchedCount());
        assertEquals(3, result.getRemovedCount());
        if (result.isModifiedCountAvailable()) {
            assertEquals(4, result.getModifiedCount());
        }
        assertEquals(Arrays.asList(new BulkWriteUpsert(1, upsertOneId),
                                   new BulkWriteUpsert(2, upsertTwoId)),
                     result.getUpserts());

        assertEquals(Arrays.<DBObject>asList(new BasicDBObject("_id", 0),
                                             new BasicDBObject("_id", 4).append("x", 1),
                                             new BasicDBObject("_id", 5).append("y", 1),
                                             new BasicDBObject("_id", 8).append("z", 3),
                                             new BasicDBObject("_id", 9).append("z", 3),
                                             new BasicDBObject("_id", upsertOneId).append("x", 2),
                                             new BasicDBObject("_id", upsertTwoId).append("y", 2)),

                     collection.find().sort(new BasicDBObject("_id", 1)).toArray());

        // when
        try {
            bulkWriteOperation.insert(new BasicDBObject());
            fail();
        } catch (IllegalStateException e) {
            // then should throw
        }

        // when
        try {
            bulkWriteOperation.find(new BasicDBObject());
            fail();
        } catch (IllegalStateException e) {
            // then should throw
        }

        // when
        try {
            bulkWriteOperation.execute();
            fail();
        } catch (IllegalStateException e) {
            // then should throw
        }

        // when
        try {
            bulkWriteOperation.execute(WriteConcern.ACKNOWLEDGED);
            fail();
        } catch (IllegalStateException e) {
            // then should throw
        }
    }

    @Test
    public void testOrderedBulkWriteOperation() {
        // given
        collection.insert(new BasicDBObject("_id", 1));

        // when
        BulkWriteOperation bulkWriteOperation = collection.initializeOrderedBulkOperation();
        bulkWriteOperation.insert(new BasicDBObject("_id", 0));
        bulkWriteOperation.insert(new BasicDBObject("_id", 1));
        bulkWriteOperation.insert(new BasicDBObject("_id", 2));

        try {
            bulkWriteOperation.execute();
            fail();
        } catch (BulkWriteException e) {
            assertEquals(1, e.getWriteErrors().size());
        }

        assertEquals(Arrays.<DBObject>asList(new BasicDBObject("_id", 0), new BasicDBObject("_id", 1)),
                     collection.find().sort(new BasicDBObject("_id", 1)).toArray());

    }

    @Test
    public void testUnorderedBulkWriteOperation() {
        // given
        collection.insert(new BasicDBObject("_id", 1));

        // when
        BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
        bulkWriteOperation.insert(new BasicDBObject("_id", 0));
        bulkWriteOperation.insert(new BasicDBObject("_id", 1));
        bulkWriteOperation.insert(new BasicDBObject("_id", 2));

        try {
            bulkWriteOperation.execute();
            fail();
        } catch (BulkWriteException e) {
            assertEquals(1, e.getWriteErrors().size());
        }

        assertEquals(Arrays.<DBObject>asList(new BasicDBObject("_id", 0), new BasicDBObject("_id", 1), new BasicDBObject("_id", 2)),
                     collection.find().sort(new BasicDBObject("_id", 1)).toArray());

    }

    @Test
    public void bulkWriteOperationShouldGenerateIdsForInserts() {
        // when
        BulkWriteOperation bulkWriteOperation = collection.initializeOrderedBulkOperation();
        BasicDBObject document = new BasicDBObject();
        bulkWriteOperation.insert(document);
        bulkWriteOperation.execute();

        // then
        assertTrue(document.containsField("_id"));
        assertTrue(document.get("_id") instanceof ObjectId);
    }


    @Test(expected = BulkWriteException.class)
    public void testBulkWriteException() {
        // given
        collection.insert(new BasicDBObject("_id", 1));

        // when
        BulkWriteOperation bulkWriteOperation = collection.initializeOrderedBulkOperation();
        bulkWriteOperation.insert(new BasicDBObject("_id", 1));
        bulkWriteOperation.execute();
    }

    @Test
    public void testWriteConcernExceptionOnInsert() throws UnknownHostException {
        assumeThat(isDiscoverableReplicaSet(), is(true));
        try {
            WriteResult res = collection.insert(new BasicDBObject(), new WriteConcern(5).withWTimeout(1, MILLISECONDS));
            fail("Write should have failed but succeeded with result " + res);
        } catch (WriteConcernException e) {
            assertEquals(0, e.getWriteConcernResult().getCount());
        }
    }

    @Test
    public void testWriteConcernExceptionOnUpdate() throws UnknownHostException {
        assumeThat(isDiscoverableReplicaSet(), is(true));
        ObjectId id = new ObjectId();
        collection.insert(new BasicDBObject("_id", id));
        try {
            WriteResult res = collection.update(new BasicDBObject("_id", id), new BasicDBObject("$set", new BasicDBObject("x", 1)),
                                                false, false, new WriteConcern(5).withWTimeout(1, MILLISECONDS));
            fail("Write should have failed but succeeded with result " + res);
        } catch (WriteConcernException e) {
            assertEquals(1, e.getWriteConcernResult().getCount());
            assertTrue(e.getWriteConcernResult().isUpdateOfExisting());
            assertEquals(null, e.getWriteConcernResult().getUpsertedId());
        }
    }

    @Test
    public void testWriteConcernExceptionOnUpsert() throws UnknownHostException {
        assumeThat(isDiscoverableReplicaSet(), is(true));
        ObjectId id = new ObjectId();
        try {
            WriteResult res = collection.update(new BasicDBObject("_id", id), new BasicDBObject("$set", new BasicDBObject("x", 1)),
                                                true, false, new WriteConcern(5).withWTimeout(1, MILLISECONDS));
            fail("Write should have failed but succeeded with result " + res);
        } catch (WriteConcernException e) {
            assertEquals(1, e.getWriteConcernResult().getCount());
            assertFalse(e.getWriteConcernResult().isUpdateOfExisting());
            assertEquals(new BsonObjectId(id), e.getWriteConcernResult().getUpsertedId());
        }
    }

    @Test
    public void testWriteConcernExceptionOnRemove() throws UnknownHostException {
        assumeThat(isDiscoverableReplicaSet(), is(true));
        try {
            collection.insert(new BasicDBObject());
            WriteResult res = collection.remove(new BasicDBObject(), new WriteConcern(5).withWTimeout(1, MILLISECONDS));
            fail("Write should have failed but succeeded with result " + res);
        } catch (WriteConcernException e) {
            assertEquals(1, e.getWriteConcernResult().getCount());
        }
    }

    @Test
    public void testBulkWriteConcernException() throws UnknownHostException {
        assumeThat(isDiscoverableReplicaSet(), is(true));
        try {
            BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
            bulkWriteOperation.insert(new BasicDBObject());
            BulkWriteResult res = bulkWriteOperation.execute(new WriteConcern(5).withWTimeout(1, MILLISECONDS));
            fail("Write should have failed but succeeded with result " + res);
        } catch (BulkWriteException e) {
            assertNotNull(e.getWriteConcernError());  // unclear what else we can reliably assert here
        }
    }

    @Test
    @Category(Slow.class)
    public void testParallelScan() throws UnknownHostException {
        assumeThat(isSharded(), is(false));
        assumeThat(serverVersionAtLeast(asList(2, 6, 0)), is(true));

        Set<Integer> ids = new HashSet<Integer>();

        for (int i = 0; i < 2000; i++) {
            ids.add(i);
            collection.insert(new BasicDBObject("_id", i));
        }

        List<Cursor> cursors = collection.parallelScan(ParallelScanOptions.builder().numCursors(3).batchSize(1000).build());
        assertTrue(cursors.size() <= 3);
        for (Cursor cursor : cursors) {
            while (cursor.hasNext()) {
                Integer id = (Integer) cursor.next().get("_id");
                assertTrue(ids.remove(id));
            }
        }

        assertTrue(ids.isEmpty());
    }

    public static class MyDBObject extends BasicDBObject {
        private static final long serialVersionUID = 3352369936048544621L;
    }

    // used via reflection
    @SuppressWarnings("UnusedDeclaration")
    public static class Tweet extends ReflectionDBObject {
        private long userId;
        private String message;
        private Date date;

        public Tweet(final long userId, final String message, final Date date) {
            this.userId = userId;
            this.message = message;
            this.date = date;
        }

        public Tweet() {
        }

        public long getUserId() {
            return userId;
        }

        public void setUserId(final long userId) {
            this.userId = userId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(final String message) {
            this.message = message;
        }

        public Date getDate() {
            return date;
        }

        public void setDate(final Date date) {
            this.date = date;
        }
    }

    public static class MyEncoder implements DBEncoder {
        @Override
        public int writeObject(final OutputBuffer outputBuffer, final BSONObject document) {
            int start = outputBuffer.getPosition();
            BsonBinaryWriter bsonWriter = new BsonBinaryWriter(outputBuffer);
            try {
                bsonWriter.writeStartDocument();
                bsonWriter.writeInt32("_id", 1);
                bsonWriter.writeString("s", "foo");
                bsonWriter.writeEndDocument();
                return outputBuffer.getPosition() - start;
            } finally {
                bsonWriter.close();
            }
        }

        public static DBObject getConstantObject() {
            return new BasicDBObject()
                   .append("_id", 1)
                   .append("s", "foo");
        }
    }

    public static class MyEncoderFactory implements DBEncoderFactory {
        @Override
        public DBEncoder create() {
            return new MyEncoder();
        }
    }

    public static class TopLevelDBObject extends BasicDBObject {
        private static final long serialVersionUID = 7029929727222305692L;
    }

    public static class NestedOneDBObject extends BasicDBObject {
        private static final long serialVersionUID = -5821458746671670383L;
    }

    public static class NestedTwoDBObject extends BasicDBObject {
        private static final long serialVersionUID = 5243874721805359328L;
    }

    private DBObject getIndexInfoForNameStartingWith(final String field) {
        for (DBObject indexInfo : collection.getIndexInfo()) {
            if (((String) indexInfo.get("name")).startsWith(field)) {
                return indexInfo;
            }
        }
        throw new IllegalArgumentException("No index for field " + field);
    }
}
