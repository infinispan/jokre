package test;

import org.infinispan.Cache;
import org.infinispan.ClassLoaderSpecfiedCache;
import org.infinispan.manager.DefaultCacheManager;
import org.jboss.jokre.agent.Jokre;

import java.util.Map;
import org.junit.Test;

public class Test3
{
    static DefaultCacheManager manager = new DefaultCacheManager();
    static Map<String, String> map = manager.getCache("number 1");
    static Cache<String, String> cache = manager.getCache("number 2");
    static Map<String, String> map2 = new ClassLoaderSpecfiedCache<String, String>(cache.getAdvancedCache(), Test3.class.getClassLoader());
    public static void main(String args[])
    {
        new Test3().runTest(args);
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

        int count = args.length;
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            final String arg = args[i];
            threads[i] = new Thread() {
                String s = arg;
                public void run() {
                    for (int j = 0; j < 400000; j++) {
                        doPut(map, arg);
                    }
                }
            };
        }
        long start1 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            threads[i].start();
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        long end1 = System.currentTimeMillis();
        Jokre.stats();
        System.out.println();
        for (int i = 0; i < count; i++) {
            final String arg = args[i];
            threads[i] = new Thread() {
                String s = arg;
                public void run() {
                    for (int j = 0; j < 400000; j++) {
                        doPut(map2, arg);
                    }
                }
            };
        }
        long start2 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            threads[i].start();
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        long end2 = System.currentTimeMillis();
        Jokre.stats();
        System.out.println();
    }

    public static void doPut(Map<String, String> map, String value)
    {
        map.put(value, value);
        value = map.put(value, value);
    }
}
