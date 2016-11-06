package party.sprz.wa2.pack;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * このクラスは、Packファイルからエントリを読み込むために使用します。
 *
 * 現在はKCAP形式のデータのみをサポートしています。
 *
 * @author Nan
 *
 */
public class PackFile implements Closeable, PackConstants {
  private final File file;
  private final int total;
  private volatile boolean closed = false;

  private PackEntry[] entryCache;
  private Map<String, InputStream> streams;

  private RandomAccessFile raf;

  /**
   * Packファイルを読込み用に開きます。
   *
   * @param name Packファイルの名前
   * @throws IOException 入出力エラーが発生した場合
   */
  public PackFile(String name) throws IOException {
    this(new File(name));
  }

  /**
   * Fileオブジェクトに指定されたPackファイルを、読込み用に開きます。
   *
   * @param file 読取りのために開くPackファイル
   * @throws IOException 入出力エラーが発生した場合
   */
  public PackFile(File file) throws IOException {
    this.raf = new RandomAccessFile(file, "r");

    PackHeaderReader peReader = new PackHeaderReader() {
      @Override
      protected int read(byte[] b) throws IOException {
        return raf.read(b);
      }
    };

    this.total = peReader.readHeader();
    this.file = file;

    this.entryCache = new PackEntry[total];
    this.streams = new HashMap<>();
    for (int i = 0; i < total; i++) {
      entryCache[i] = peReader.readEntryHeader();
    }

    byte[] b = new byte[4];
    for (int i = 0; i < total; i++) {
      if (entryCache[i].getMethod() == 1) {
        raf.seek(entryCache[i].getOffset() + SIZE_OFF);
        raf.read(b);
        entryCache[i].setSize(get32(b, 0));
      } else {
        entryCache[i].setSize(entryCache[i].getCompressedSize());
      }
    }
  }

  /**
   * 指定された名前のPackファイル・エントリを返します。見つからない場合は、nullを返します。
   *
   * @param name エントリの名前
   * @return Packファイル・エントリ。見つからない場合はnull
   * @throws IllegalStateException - Packファイルが閉じられている場合
   */
  public PackEntry getEntry(String name) {
    Objects.requireNonNull(name);
    synchronized (this) {
      ensureOpen();
      int entryIndex = indexOf(name);
      if (entryIndex != -1) {
        PackEntry e = entryCache[entryIndex];
        return e;
      }
    }
    return null;
  }

  private int indexOf(String name) {
    for (int i = 0; i < total; i++) {
      PackEntry e = entryCache[i];
      if (e.getName().equals(name)) {
        return i;
      }
    }
    return -1;
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("PackFile closed");
    }
  }

  private void ensureOpenOrPackException() throws PackException {
    if (closed) {
      throw new PackException("PackFile closed");
    }
  }

  /**
   * Packファイルを閉じます。
   *
   * このPackファイルを閉じると、getInputStreamメソッドの呼出しにより以前に返されたすべての入力ストリームが閉じられます。
   *
   * @throws IOException 入出力エラーが発生した場合
   *
   * @see java.io.Closeable#close()
   */
  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    entryCache = null;
    for (InputStream in : streams.values()) {
      if (in != null) {
        in.close();
      }
    }
    closed = true;
  }

  /**
   * Packファイルのパス名を返します。
   *
   * @return Packファイルのパス名
   */
  public String getName() {
    return file.getPath();
  }

  /**
   * Packファイル中のエントリの数を返します。
   *
   * @return Packファイル中のエントリの数
   * @throws IllegalStateException - Packファイルが閉じられている場合
   */
  public int size() {
    ensureOpen();
    return total;
  }

  /**
   * Packファイルのエントリに対する順序付けされたStreamを返します。
   *
   * @return このPackファイル内のエントリの順序付けされたStream
   * @throws IllegalStateException - Packファイルが閉じられている場合
   */
  public Stream<? extends PackEntry> stream() {
    int characteristics =
        Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.IMMUTABLE | Spliterator.NONNULL;
    return StreamSupport
        .stream(Spliterators.spliterator(new PackEntryIterator(), size(), characteristics), false);
  }

  /**
   * 指定されたPackファイル・エントリの内容を読み込む入力ストリームを返します。
   *
   * このPackファイルを閉じると、このメソッドの呼出しにより返されたすべての入力ストリームが閉じられます。
   *
   * @param entry Packファイル・エントリ
   * @return 指定されたPackファイル・エントリの内容を読み込む入力ストリーム。
   * @throws PackException Pack形式エラーが発生した場合
   * @throws IOException 入出力エラーが発生した場合
   * @throws IllegalStateException Packファイルが閉じられている場合
   */
  public InputStream getInputStream(PackEntry entry) throws IOException {
    Objects.requireNonNull(entry);
    if (streams.get(entry.getName()) == null) {
      InputStream in = createInputStream(entry);
      if (in != null) {
        streams.put(entry.getName(), createInputStream(entry));
      }
    }
    return streams.get(entry.getName());
  }

  public InputStream createInputStream(PackEntry entry) throws IOException {
    InputStream in = null;
    synchronized (this) {
      ensureOpen();
      if (entry.getMethod() == 1) {
        in = new PackFileLZSSInputStream(file, entry);
      } else {
        in = new PackFileInputStream(file, entry);
      }
    }
    return in;
  }

  /**
   * このPackFileオブジェクトによって保持されているシステム・リソースを、それへの参照がなくなったときに解放されるようにします。
   *
   * GCでこのメソッドが呼び出される時間が決まっていないため、このPackFileへのアクセスが完了した直後に、アプリケーションでcloseメソッドを呼び出すことを強くお薦めします。
   * これにより、時間が定まらないままシステム・リソースが保持されるのを防止できます。
   *
   * @throws IOException 入出力エラーが発生した場合
   *
   * @see java.lang.Object#finalize()
   */
  @Override
  protected void finalize() throws IOException {
    close();
  }

  /**
   * Packファイル・エントリの列挙を返します。
   *
   * @return Packファイル・エントリの列挙
   * @throws IllegalStateException Packファイルが閉じられている場合
   */
  public Enumeration<? extends PackEntry> entries() {
    return new PackEntryIterator();
  }

  private class PackEntryIterator implements Enumeration<PackEntry>, Iterator<PackEntry> {
    private int i = 0;

    public PackEntryIterator() {
      ensureOpen();
    }

    @Override
    public boolean hasNext() {
      synchronized (PackFile.this) {
        ensureOpen();
        return i < total;
      }
    }

    @Override
    public PackEntry next() {
      synchronized (PackFile.this) {
        ensureOpen();
        if (i >= total) {
          throw new NoSuchElementException();
        }
        PackEntry e = entryCache[i++];
        return e;
      }
    }

    @Override
    public boolean hasMoreElements() {
      return hasNext();
    }

    @Override
    public PackEntry nextElement() {
      return next();
    }
  }

  private class PackFileLZSSInputStream extends InputStream {
    private LZSSDecompresser decomp;

    public PackFileLZSSInputStream(File file, PackEntry e) throws IOException {
      try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
        MappedByteBuffer buf =
            raf.getChannel().map(MapMode.READ_ONLY, e.getOffset(), e.getCompressedSize());
        decomp = new LZSSDecompresser(buf);
      }
    }

    @Override
    public void close() throws IOException {
      decomp.close();
    }

    @Override
    public int available() throws IOException {
      return decomp.available();
    }

    @Override
    protected void finalize() throws Throwable {
      close();
    }

    @Override
    public int read() throws IOException {
      byte[] b = new byte[1];
      if (read(b, 0, 1) == 1) {
        return b[0] & 0xff;
      }
      return -1;
    }

    @Override
    public int read(byte b[]) throws IOException {
      return read(b, 0, b.length);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
      return decomp.decompress(b, off, len);
    }
  }

  private class PackFileInputStream extends InputStream {
    private volatile boolean closed = false;
    private int dataRemaining;

    private ByteBuffer dataBuf;

    private byte[] tmpBuf = new byte[512];

    public PackFileInputStream(File file, PackEntry e) throws IOException {
      Objects.requireNonNull(e);
      int size = e.getSize();
      this.dataRemaining = size;

      if (0 < size) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
          this.dataBuf = raf.getChannel().map(MapMode.READ_ONLY, e.getOffset(), size);
        }
      }
    }

    @Override
    public int read() throws IOException {
      byte[] b = new byte[1];
      if (read(b, 0, 1) == 1) {
        return b[0] & 0xff;
      }
      return -1;
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
      if (off < 0 || len < 0 || off > b.length - len) {
        throw new IndexOutOfBoundsException();
      } else if (len == 0) {
        return 0;
      }
      if (closed) {
        return -1;
      }
      if (dataRemaining <= 0) {
        close();
        return -1;
      }

      synchronized (PackFile.this) {
        int newLength = len;
        if (dataRemaining < len) {
          newLength = dataRemaining;
        }
        ensureOpenOrPackException();

        dataBuf.get(b, off, newLength);
        dataRemaining = dataBuf.remaining();

        return newLength;
      }
    }

    @Override
    public long skip(long n) throws IOException {
      if (n < 0) {
        throw new IllegalArgumentException("negative skip length");
      }
      ensureOpen();

      long curr = 0;
      long len;
      while (curr < n) {
        len = n - curr;
        if (len > tmpBuf.length) {
          len = tmpBuf.length;
        }
        len = read(tmpBuf, 0, (int) len);
        if (len == -1) {
          close();
          break;
        }
        curr += len;
      }
      return curr;
    }

    @Override
    public int available() throws IOException {
      ensureOpen();
      if (dataRemaining == 0) {
        return 0;
      }
      return 1;
    }

    private void ensureOpen() {
      if (closed) {
        throw new NullPointerException("Decompresser has been closed");
      }
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      dataRemaining = 0;
      dataBuf = null;
    }

    @Override
    protected void finalize() throws IOException {
      close();
    }
  }

  private int get32(byte[] b, int off) {
    return ByteBuffer.wrap(b, off, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
  }
}
