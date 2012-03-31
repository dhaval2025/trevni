/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.trevni.avro;

import java.io.IOException;
import java.io.File;
import java.io.OutputStream;
import java.util.Collection;

import org.apache.trevni.ColumnFileMetaData;
import org.apache.trevni.ColumnFileWriter;
import org.apache.trevni.TrevniRuntimeException;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.generic.GenericData;

import static org.apache.trevni.avro.AvroColumnator.isSimple;

/** Write Avro records to a Trevni column file.  Each primitive type is written
 * to a separate column. */
public class AvroColumnWriter<D> {
  private Schema schema;
  private GenericData model;
  private ColumnFileWriter writer;
  private int[] arrayWidths;

  public static final String SCHEMA_KEY = "avro.schema";

  public AvroColumnWriter(Schema s, ColumnFileMetaData meta)
    throws IOException {
    this(s, meta, GenericData.get());
  }

  public AvroColumnWriter(Schema s, ColumnFileMetaData meta, GenericData model)
    throws IOException {
    this.schema = s;
    AvroColumnator columnator = new AvroColumnator(s);
    meta.set(SCHEMA_KEY, s.toString());           // save schema in file
    this.writer = new ColumnFileWriter(meta, columnator.getColumns());
    this.arrayWidths = columnator.getArrayWidths();
    this.model = model;
  }

  /** Write all rows added to the named output stream. */
  public void writeTo(OutputStream out) throws IOException {
    writer.writeTo(out);
  }

  /** Write all rows added to the named file. */
  public void writeTo(File file) throws IOException {
    writer.writeTo(file);
  }

  /** Add a row to the file. */
  public void write(D value) throws IOException {
    writer.startRow();
    int count = write(value, schema, 0);
    assert(count == writer.getColumnCount());
    writer.endRow();
  }
  
  private int write(Object o, Schema s, int column) throws IOException {
    if (isSimple(s)) {
      writer.writeValue(o, column);
      return column+1;
    }
    switch (s.getType()) {
    case MAP: 
      throw new TrevniRuntimeException("Unknown schema: "+s);
    case RECORD: 
      for (Field f : s.getFields())
        column = write(model.getField(o,f.name(),f.pos()), f.schema(), column);
      return column;
    case ARRAY: 
      Collection elements = (Collection)o;
      writer.writeLength(elements.size(), column);
      if (isSimple(s.getElementType())) {         // optimize simple arrays
        for (Object element : elements)
          writer.writeValue(element, column);
        return column+1;
      }
      for (Object element : elements) {
        writer.writeValue(null, column);
        int c = write(element, s.getElementType(), column+1);
        assert(c == column+arrayWidths[column]);
      }
      return column+arrayWidths[column];
    case UNION:
      int b = model.resolveUnion(s, o);
      int i = 0;
      for (Schema branch : s.getTypes()) {
        boolean selected = i++ == b;
        if (!selected) {
          writer.writeLength(0, column);
          column+=arrayWidths[column];
        } else {
          writer.writeLength(1, column);
          if (isSimple(branch)) {
            writer.writeValue(o, column++);
          } else {
            writer.writeValue(null, column);
            column = write(o, branch, column+1);
          }
        }
      }
      return column;
    default:
      throw new TrevniRuntimeException("Unknown schema: "+s);
    }
  }

}