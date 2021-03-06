/*
 * Copyright 2015 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.operation

import org.bson.BsonBoolean
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.codecs.BsonDocumentCodec
import spock.lang.IgnoreIf
import spock.lang.Specification

import static com.mongodb.ClusterFixture.getBinding
import static com.mongodb.ClusterFixture.isSharded


class FsyncUnlockOperationSpecification extends Specification {
    @IgnoreIf({ isSharded() })
    def 'should unlock server'() {
        given:
        new CommandWriteOperation('admin', new BsonDocument('fsync', new BsonInt32(1)).append('lock', new BsonInt32(1)),
                                  new BsonDocumentCodec())
                .execute(getBinding())

        when:
        def result = new FsyncUnlockOperation().execute(getBinding())

        then:
        result
        result.containsKey('ok')
        result.containsKey('info')
        !new CurrentOpOperation().execute(getBinding()).getBoolean('fsyncLock', BsonBoolean.FALSE).getValue()
    }

}
