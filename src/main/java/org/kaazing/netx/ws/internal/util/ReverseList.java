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

package org.kaazing.netx.ws.internal.util;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class ReverseList<T> implements Iterable<T> {
    private final ListIterator<T> _reverseIterator;

    public ReverseList(List<T> source) {
        this._reverseIterator = source.listIterator(source.size());
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {

            @Override
            public boolean hasNext() {
                return _reverseIterator.hasPrevious();
            }

            @Override
            public T next() {
                return _reverseIterator.previous();
            }

            @Override
            public void remove() {
                _reverseIterator.remove();
            }
        };
    }

}
