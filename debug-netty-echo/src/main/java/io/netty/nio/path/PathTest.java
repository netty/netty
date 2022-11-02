package io.netty.nio.path;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author lxcecho 909231497@qq.com
 * @since 15.09.2021
 */
public class PathTest {

    @Test
    public void testPath() throws Exception {
        Path path = Paths.get("D:\\file01.txt");

        Path test = Paths.get("D:\\images", "test");

        Path file = Paths.get("D:\\images", "test\\01.txt");

        String originalPath = "d:\\test\\projects\\..\\yygh-project";

        Path path1 = Paths.get(originalPath);
        System.out.println("path1 = " + path1);

        Path path2 = path1.normalize();
        System.out.println("path2 = " + path2);
    }

}
