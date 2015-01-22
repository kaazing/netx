/*
 * Copyright 2014, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaazing.netx.http.bridge.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

public final class FileFunctions {

    @Function
    public static String length(String path) {
        return Long.toString(new File(path).length());
    }

    @Function
    public static byte[] bytes(String path) {
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            byte[] array = new byte[in.available()];
            in.read(array);
            return array;
        }
        catch (IOException e) {
            return new byte[0];
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective {

        public Mapper() {
            super(FileFunctions.class);
        }

        @Override
        public String getPrefixName() {
            return "file";
        }

    }

    private FileFunctions() {
        // utility
    }

}

