package party.sprz.wa2.pack;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * このクラスは、Packファイル形式でファイルを読み込む入力ストリーム・フィルタを実装します。
 *
 * 現在はKCAP形式のデータのみをサポートしています。
 *
 * @author Nan
 *
 */
public class PackInputStream extends InputStream implements PackConstants {
  private PackEntry entry;
  private int dataRemaining = -1;
  private int entryRemaining = -1;

  private boolean closed = false;
  private boolean entryEOF = true;

  private PackEntry[] entryCache;

  private InputStream in;
  private LZSSDecompresser decomp;

  private byte[] tmpBuf = new byte[512];

  /**
   * 新しいPack入力ストリームを作成します。
   *
   * @param in 実際の入力ストリーム
   */
  public PackInputStream(InputStream in) {
    Objects.requireNonNull(in);
    this.in = in;
  }

  /**
   * 現在のPackエントリから1バイトを読み込みます。
   *
   * @throws NullPointerException bがnullである場合。
   * @throws IllegalArgumentException offが負の値の場合、lenが負の値の場合、またはlenがb.length - offより大きい場合
   * @throws PackException - Packファイル・エラーが発生した場合
   * @throws IOException 入出力エラーが発生した場合
   *
   * @see java.io.InputStream#read()
   */
  @Override
  public int read() throws IOException {
    ensureOpen();
    byte[] singleByteBuf = new byte[1];
    return read(singleByteBuf, 0, 1) == -1 ? -1 : Byte.toUnsignedInt(singleByteBuf[0]);
  }

  /**
   * 現在のPackエントリからバイト配列に読み込みます。
   *
   * @param b データの読込み先のバッファ
   *
   * @throws NullPointerException bがnullである場合。
   * @throws IllegalArgumentException offが負の値の場合、lenが負の値の場合、またはlenがb.length - offより大きい場合
   * @throws PackException - Packファイル・エラーが発生した場合
   * @throws IOException 入出力エラーが発生した場合
   *
   * @see java.io.InputStream#read(byte[])
   */
  @Override
  public int read(byte[] b) throws IOException {
    return read(b, 0, b.length);
  }

  /**
   * 現在のPackエントリからバイト配列に読み込みます。
   *
   * @param b データの読込み先のバッファ
   * @param off 転送先配列bの開始オフセット
   * @param len 読み込まれる最大バイト数
   *
   * @throws NullPointerException bがnullである場合。
   * @throws IllegalArgumentException offが負の値の場合、lenが負の値の場合、またはlenがb.length - offより大きい場合
   * @throws PackException - Packファイル・エラーが発生した場合
   * @throws IOException 入出力エラーが発生した場合
   *
   * @see java.io.InputStream#read(byte[], int, int)
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    Objects.requireNonNull(b);
    if (off < 0 || len < 0 || off > b.length - len) {
      throw new IndexOutOfBoundsException();
    } else if (len == 0) {
      return 0;
    }
    ensureOpen();

    if (entry == null) {
      return -1;
    }

    if (dataRemaining <= 0) {
      if (decomp != null) {
        decomp.close();
      }
      entryEOF = true;
      entry = null;
      return -1;
    }

    int readLength = len;
    if (dataRemaining <= len) {
      readLength = dataRemaining;
    }

    int r;
    if (entry.getMethod() == 1) {
      r = decomp.decompress(b, off, readLength);
    } else {
      r = in.read(b, off, readLength);
    }

    if (r == -1) {
      throw new PackException("Data is broken (data size does'nt match)");
    }

    dataRemaining -= r;
    return r;
  }

  /**
   * 現在のPackエントリで指定したバイト数だけスキップします。
   *
   * @param n スキップするバイト数
   *
   * @throws PackException - Packファイル・エラーが発生した場合
   * @throws IOException 入出力エラーが発生した場合
   * @throws IllegalArgumentException n < 0の場合
   *
   * @see java.io.InputStream#skip(long)
   */
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
        entryEOF = true;
        break;
      }
      curr += len;
    }
    return curr;
  }

  /**
   * 次のPackファイル・エントリを読み取って、エントリ・データの先頭にストリームを配置します。
   *
   * @return 次のPackファイル・エントリ。エントリがこれ以上存在しない場合はnull
   * @throws PackException - Packファイル・エラーが発生した場合
   * @throws IOException 入出力エラーが発生した場合
   */
  public PackEntry getNextEntry() throws IOException {
    ensureOpen();
    if (entry != null) {
      closeEntry();
    }

    if ((entry = readHeader()) == null) {
      return null;
    }
    dataRemaining = entry.getSize();
    entryEOF = false;

    return entry;
  }

  /**
   * 現在のPackエントリを閉じ、次のエントリを読み込むためにストリームを配置します。
   *
   * @throws PackException - Packファイル・エラーが発生した場合
   * @throws IOException 入出力エラーが発生した場合
   */
  public void closeEntry() throws IOException {
    ensureOpen();
    while (read(tmpBuf, 0, tmpBuf.length) != -1) {
    }
  }


  private PackEntry readHeader() throws IOException {
    if (entryRemaining == -1) {
      makeEntryHeader();
    } else if (entryRemaining == 0) {
      return null;
    }

    int entryIndex = entryCache.length - entryRemaining;
    PackEntry e = entryCache[entryIndex];

    int size;
    // LZSS compressed file
    if (e.getMethod() == 1) {
      byte[] b = new byte[e.getCompressedSize()];
      in.read(b);
      decomp = new LZSSDecompresser(b);
      size = decomp.getSize();
    }
    // Uncompressed file
    else {
      size = e.getCompressedSize();
    }
    e.setSize(size);

    entryRemaining--;
    return e;
  }

  private void makeEntryHeader() throws IOException {
    PackHeaderReader peReader = new PackHeaderReader() {
      @Override
      protected int read(byte[] b) throws IOException {
        return in.read(b);
      }
    };

    int entryCount = peReader.readHeader();

    entryCache = new PackEntry[entryCount];
    for (int i = 0; i < entryCount; i++) {
      entryCache[i] = peReader.readEntryHeader();
    }
    entryRemaining = entryCount;
  }

  private void ensureOpen() throws IOException {
    if (closed) {
      throw new IOException("Stream closed");
    }
  }

  /**
   * 現在の入力データのEOFに達したあとで呼び出した場合に0を返します。そうでない場合は常に1を返します。
   *
   * このメソッドは、ブロックなしで読み込める実際のバイト数を返すためのものではありません。
   *
   * @return EOFの前では1、EOFが現在のエントリに達したあとでは0。
   * @throws IOException 入出力エラーが発生した場合
   *
   * @see java.io.InputStream#available()
   */
  @Override
  public int available() throws IOException {
    ensureOpen();
    if (entryEOF) {
      return 0;
    }
    return 1;
  }

  /**
   * この入力ストリームを閉じて、そのストリームに関連するすべてのシステム・リソースを解放します。
   *
   * @throws IOException 入出力エラーが発生した場合
   *
   * @see java.io.InputStream#close()
   */
  @Override
  public void close() throws IOException {
    in.close();
    if (decomp != null) {
      decomp.close();
    }
    entry = null;
    entryCache = null;
    closed = true;
  }

  /**
   * この入力ストリームがmarkおよびresetメソッドをサポートしているかどうかを判定します。PackInputStreamのmarkSupportedメソッドはfalseを返します。
   *
   * @return このストリームの型がmarkおよびresetメソッドをサポートしているかどうかを示すboolean。
   *
   * @see java.io.InputStream#markSupported()
   */
  @Override
  public boolean markSupported() {
    return false;
  }

  /**
   * この入力ストリームの現在位置にマークを設定します。
   *
   * PackInputStreamのmarkメソッドは何も行いません。
   *
   * @see java.io.InputStream#mark(int)
   */
  @Override
  public void mark(int readLimit) {}

  /**
   * このストリームを、この入力ストリームで最後にmarkメソッドが呼び出されたときの位置に再配置します。
   *
   * PackInputStreamクラスのresetメソッドはIOExceptionをスローする以外何も行いません。
   *
   * @throws IOException 入出力エラーが発生した場合
   *
   * @see java.io.InputStream#reset()
   */
  @Override
  public void reset() throws IOException {
    throw new IOException("mark/reset not supported");
  }
}
