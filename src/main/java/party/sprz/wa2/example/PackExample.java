package party.sprz.wa2.example;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;

import party.sprz.wa2.pack.PackEntry;
import party.sprz.wa2.pack.PackFile;
import party.sprz.wa2.pack.PackInputStream;

public class PackExample {
  /**
   * PackFileを利用した読み込みの例です。
   */
  public static void readUsingPackFile() {
    try (PackFile pf = new PackFile("C:\\Leaf\\WHITE ALBUM2\\script.PAK")) {
      Files.createDirectories(Paths.get("out"));
      for (Enumeration<? extends PackEntry> e = pf.entries(); e.hasMoreElements();) {
        PackEntry entry = e.nextElement();
        System.out.println(entry.getName());
        try (InputStream is = pf.getInputStream(entry)) {
          byte[] b = new byte[entry.getSize()];
          is.read(b);
          try (FileOutputStream output = new FileOutputStream("out/" + entry.getName())) {
            output.write(b);
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * PackInputStreamを利用した読み込みの例です。
   */
  public static void readUsingPackInputStream() {
    try (PackInputStream pis =
        new PackInputStream(new FileInputStream("C:\\Leaf\\WHITE ALBUM2\\script.PAK"))) {
      Files.createDirectories(Paths.get("out"));
      PackEntry entry;
      while ((entry = pis.getNextEntry()) != null) {
        System.out.println(entry.getName());
        byte[] b = new byte[entry.getSize()];
        pis.read(b);
        try (FileOutputStream output = new FileOutputStream("out/" + entry.getName())) {
          output.write(b);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    readUsingPackInputStream();
  }
}
