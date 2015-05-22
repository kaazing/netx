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

package org.kaazing.netx.http.internal.auth;

import static org.kaazing.netx.http.internal.auth.NodeTest.NodeType.TEST;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.kaazing.netx.http.internal.auth.DefaultDispatchChallengeHandler.Node;

public class NodeTest {

    private DefaultDispatchChallengeHandler.Node<String, NodeType> root;

    static enum NodeType {
        TEST
    }

    @Before
    public void setUp() throws Exception {
        root = new DefaultDispatchChallengeHandler.Node<String, NodeType>();
    }

    @Test
    public void testAccessRootNode() throws Exception {
        Assert.assertNull(root.getName());
        Assert.assertNull(root.getParent());
        Assert.assertTrue(root.isRootNode());
    }

    @Test
    public void testMakeChild() throws Exception {
        root.addChild("foo", TEST);
        Node<String, NodeType> fooNode = root.getChild("foo");
        Assert.assertEquals("foo", fooNode.getName());
        Assert.assertSame(root, fooNode.getParent());
    }

    @Test
    public void testFullyQualifiedName() throws Exception {
        Assert.assertEquals("", root.getFullyQualifiedName());
        root.addChild("foo", TEST);
        Node<String, NodeType> foo = root.getChild("foo");
        Assert.assertEquals("foo", foo.getFullyQualifiedName());

        foo.addChild("bar", TEST);
        Node<String, NodeType> bar = foo.getChild("bar");
        Assert.assertEquals("foo.bar", bar.getFullyQualifiedName());
    }

    @Test
    public void testCannotFindChildWithWrongName() throws Exception {
        Assert.assertNull(root.getChild("bar"));
        root.addChild("foo", TEST);
        Assert.assertNull(root.getChild("bar"));

    }

    @Test
    public void testWildcard() throws Exception {
        Assert.assertFalse(root.isWildcard());
        root.addChild("foo", TEST);
        Node<String, NodeType> foo = root.getChild("foo");
        Assert.assertFalse(foo.isWildcard());
    }


    @Test(expected = IllegalArgumentException.class)
    public void testSetValueOnRootNodeIllegal() throws Exception {
        root.appendValues("VALUE");
    }

    @Test
    public void testSetValue() {
        root.addChild("foo", TEST);
        DefaultDispatchChallengeHandler.Node<String, NodeType> foo = root.getChild("foo");
        foo.appendValues("VALUE");
        Assert.assertEquals(Arrays.asList("VALUE"), foo.getValues());
    }

    @Test
    public void testGetParent() throws Exception {
        root.addChild("foo", TEST);
        Node<String, NodeType> foo = root.getChild("foo");
        foo.addChild("bar", TEST);
        Node<String, NodeType> bar = foo.getChild("bar");

        Assert.assertNull(root.getParent());
        Assert.assertEquals(root, foo.getParent());
        Assert.assertEquals(foo, bar.getParent());
    }

    @Test
    public void testHasChildren() throws Exception {
        Assert.assertFalse(root.hasChildren());
        root.addChild("foo", TEST);
        Assert.assertTrue(root.hasChildren());
    }

    @Test
    public void testIsWildcard() throws Exception {
        Assert.assertFalse(root.isWildcard());
        root.addChild("foo", TEST);
        Assert.assertFalse(root.isWildcard());
        Node<String, NodeType> foo = root.getChild("foo");
        Assert.assertFalse(foo.isWildcard());

        foo.addChild(DefaultDispatchChallengeHandler.Node.getWildcardChar(), TEST);
        Assert.assertFalse(foo.isWildcard());
        Assert.assertTrue(foo.getChild(DefaultDispatchChallengeHandler.Node.getWildcardChar()).isWildcard());
    }

    @Test
    public void testHasWildcardDefined() throws Exception {
        root.addChild("foo", TEST);
        Node<String, NodeType> foo = root.getChild("foo");
        foo.addChild(DefaultDispatchChallengeHandler.Node.getWildcardChar(), TEST);
        Assert.assertTrue(foo.hasWildcardChild());
        Assert.assertTrue(foo.getChild(DefaultDispatchChallengeHandler.Node.getWildcardChar()).isWildcard());
        Assert.assertFalse(foo.isWildcard());
        Assert.assertFalse(foo.getParent().isWildcard());
    }

    @Test
    public void testGetWildcard() throws Exception {
        root.addChild("foo", TEST);
        Node<String, NodeType> foo = root.getChild("foo");
        foo.addChild(DefaultDispatchChallengeHandler.Node.getWildcardChar(), TEST);
        Node<String, NodeType> fooChild = foo.getChild(DefaultDispatchChallengeHandler.Node.getWildcardChar());
        // Wildcard nodes know they are wildcards.
        Assert.assertTrue(fooChild.isWildcard());
        // Parents of wildcards know they have wildcard children.
        Assert.assertTrue(foo.hasWildcardChild());

    }



    @Test(expected = IllegalArgumentException.class)
    public void testCannotCreateANullNamedNode() throws Exception {
        root.addChild(null, TEST);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCannotCreateAnEmptyNamedNode() throws Exception {
        root.addChild("", TEST);
    }
}
