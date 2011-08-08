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
