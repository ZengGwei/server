/*
 * ToroDB Server
 * Copyright © 2014 8Kdata Technology (www.8kdata.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.torodb.standalone.utils;

import com.mongodb.MongoClient;

import java.util.function.Supplier;

/**
 * A MongoClient {@link Supplier} that can be closed.
 */
public interface ClientSupplier extends AutoCloseable, Supplier<MongoClient>{

  /**
   * Returns a {@link MongoClient} that can be used to talk with a ToroDB Server instance.
   */
  @Override
  public MongoClient get();

  @Override
  public void close();

}
