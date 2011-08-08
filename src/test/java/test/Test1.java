package test;

import org.infinispan.manager.DefaultCacheManager;

import java.util.Map;

import org.jboss.jokre.agent.Jokre;
import org.junit.Test;

public class Test1
{
    static Map<String, String> map = new DefaultCacheManager().getCache();
    static Map<String, String> map2 = new TestMap<String, String>();

    public static void main(String args[])
    {
        new Test1().runTest(args);
    }

    @Test
    public void test()
    {
        runTest(null);
        Jokre.stats();
    }

    public void runTest(String[] args)
    {
        if (args == null || args.length == 0) {
            args = new String[] { "foo", "bar", "bax", "mumble", "grumble", "bletch"};
        }
        for (String arg : args) {
            doPut(map, arg);
            doPut(map2, arg);
        }
    }

    public static void doPut(Map<String, String> map, String value)
    {
        map.put(value, value);
        value = map.put(value, value);
    }
}
