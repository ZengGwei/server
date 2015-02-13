/*
 *     This file is part of ToroDB.
 *
 *     ToroDB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ToroDB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with ToroDB. If not, see <http://www.gnu.org/licenses/>.
 *
 *     Copyright (c) 2014, 8Kdata Technology
 *     
 */

package com.torodb.torod.db.postgresql.meta;

import com.google.common.collect.Multimap;
import com.torodb.torod.core.language.projection.Projection;
import com.torodb.torod.core.subdocument.SplitDocument;
import com.torodb.torod.core.subdocument.structure.DocStructure;
import com.torodb.torod.db.postgresql.meta.routines.*;
import java.util.List;
import org.jooq.Configuration;

/**
 * This class is generated by jOOQ.
 * <p>
 * Convenience access to all stored procedures and functions in public
 */
@javax.annotation.Generated(value = {"http://www.jooq.org", "3.3.2"},
        comments = "This class is generated by jOOQ")
@java.lang.SuppressWarnings({"all", "unchecked", "rawtypes"})
public class Routines {

    /**
     * Call <code>public.create_collection_schema(collection varchar)</code>
     * <p>
     * @param configuration
     * @param collection
     */
    public static void createCollectionSchema(Configuration configuration, String collection) {
        CreateCollectionSchema f = new CreateCollectionSchema();
        f.setCollection(collection);

        f.execute(configuration);
    }

    /**
     * Call <code>public.first_free_value_type_id</code>
     * @param configuration
     * @param colSchema
     * @return 
     */
    public static Integer firstFreeDocId(Configuration configuration, String colSchema) {
        com.torodb.torod.db.postgresql.meta.routines.FirstFreeDocId f = new com.torodb.torod.db.postgresql.meta.routines.FirstFreeDocId();
        f.setColSchema(colSchema);

        f.execute(configuration);
        return f.getReturnValue();
    }

    /**
     * Call <code>public.reserve_value_type_id</code>
     * @param configuration
     * @param colSchema
     * @param increment
     * @return 
     */
    public static Integer reserveDocIds(Configuration configuration, String colSchema, Integer increment) {
        com.torodb.torod.db.postgresql.meta.routines.ReserveDocIds f = new com.torodb.torod.db.postgresql.meta.routines.ReserveDocIds();
        f.setColSchema(colSchema);
        f.setIncrement(increment);

        f.execute(configuration);
        return f.getReturnValue();
    }

    public static List<SplitDocument> readDocuments(Configuration configuration, CollectionSchema colSchema, Integer[] requestedDocs, Projection projection) {
        return QueryRoutine.execute(configuration, colSchema, requestedDocs, projection);
    }
    
    public static int deleteDocuments(Configuration configuration, CollectionSchema colSchema, Multimap<DocStructure, Integer> didsByStructure, boolean justOne) {
        return DeleteDocuments.execute(configuration, colSchema, didsByStructure, justOne);
    }

    public static void dropCollection(Configuration jooqConf, CollectionSchema colSchema) {
        DropCollection.execute(jooqConf, colSchema);
    }
}
