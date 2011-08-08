/*
* JBoss, Home of Professional Open Source
* Copyright 2011, Red Hat and individual contributors
* by the @authors tag.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*
* @authors Andrew Dinn
*/
package test;

import org.infinispan.manager.DefaultCacheManager;
import org.jboss.jokre.agent.Jokre;

import java.util.Map;
import org.junit.Test;

public class Test2
{
    static Map<String, String> map = new DefaultCacheManager().getCache();
    static Map<String, String> map2 = new TestMap<String, String>();

    public static void main(String args[])
    {
        new Test2().runTest(args);
    }

    @Test
    public void test()
    {
        runTest(null);
    }

    public void runTest(String[] args)
    {
        if (args == null || args.length == 0) {
            args = new String[] { "foo", "bar", "bax", "mumble", "grumble", "bletch"};
        }

        long start1 = System.currentTimeMillis();
        for (int i = 0; i < 400000; i++) {
            for (String arg : args) {
                doPut(map, arg);
            }
        }
        long end1 = System.currentTimeMillis();
        Jokre.stats();
        System.out.println();
        long start2 = System.currentTimeMillis();
        for (String arg : args) {
            doPut(map2, arg);
        }
        long end2 = System.currentTimeMillis();
        long start3 = System.currentTimeMillis();
        for (int i = 0; i < 400000; i++) {
            for (String arg : args) {
                doPut(map, arg);
            }
        }
        long end3 = System.currentTimeMillis();
        Jokre.stats();
        System.out.println();
        long start4 = System.currentTimeMillis();
        for (int i = 0; i < 400000; i++) {
            for (String arg : args) {
                doPut(map2, arg);
            }
        }
        long end4 = System.currentTimeMillis();
    }

    public static void doPut(Map<String, String> map, String value)
    {
        map.put(value, value);
        value = map.put(value, value);
    }
}
