package io.netty.nio.file;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author lxcecho 909231497@qq.com
 * @since 15.09.2021
 */
public class FileTest {

    @Test
    public void testNioFile() {
        //createDirectory
//        Path path = Paths.get("d:\\test");
//        try {
//            Path directory = Files.createDirectory(path);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        //copy
//        Path path1 = Paths.get("d:\\test\\01.txt");
//        Path path2 = Paths.get("d:\\test\\001.txt");
//        try {
//            Path copy = Files.copy(path1, path2, StandardCopyOption.REPLACE_EXISTING);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        //move
//        Path sourcePath = Paths.get("d:\\test\\01.txt");
//        Path destinationPath = Paths.get("d:\\test\\01test.txt");
//
//        try {
//            Files.move(sourcePath, destinationPath,
//                    StandardCopyOption.REPLACE_EXISTING);
//        } catch (IOException e) {
//            //移动文件失败
//            e.printStackTrace();
//        }


        //delete
//        Path path = Paths.get("d:\\test\\001.txt");
//        try {
//            Files.delete(path);
//        } catch (IOException e) {
//            // 删除文件失败
//            e.printStackTrace();
//        }


        Path path = Paths.get("D:\\images");
        String fileToFind = File.separator + "show0.jpg";

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String fileString = file.toAbsolutePath().toString();

                    if(fileString.endsWith(fileToFind)){
                        System.out.println("file found at path : "+ file.toAbsolutePath());
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

}
