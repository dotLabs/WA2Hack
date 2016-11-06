package party.sprz.wa2.pack;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.Objects;

/**
 * このクラスは、Packファイル形式で使われているLZSS圧縮解除をサポートします。
 *
 * @author Nan
 *
 */
public class LZSSDecompresser implements Closeable, PackConstants {
  public final static int SLIDING_WINDOW_SIZE = 0x1000;
  public final static int MAXIMUM_REFERENCE_LENGTH = 0x12;

  private int compressedSize = -1;
  private int originalSize = -1;

  private byte[] dicTable = new byte[SLIDING_WINDOW_SIZE];
  private int currDicPos = SLIDING_WINDOW_SIZE - MAXIMUM_REFERENCE_LENGTH;

  private int rDataRemaining = 0;
  private int wDataRemaining = 0;

  private BitSet flag = new BitSet(BLOCKDATA_SIZE);
  private int blockDataRemaining = 0;

  private int referenceLength = 0;
  private int referenceRemaining = 0;
  private byte[] referenceCache = new byte[MAXIMUM_REFERENCE_LENGTH];

  private boolean closed;
  private int bytesRead;
  private int bytesWritten;

  private ByteBuffer dataBuf;

  /**
   * 新しいデコンプレッサを作成します。
   *
   * @param b Packファイル内の1エントリ分のデータ領域のバイト列
   */
  public LZSSDecompresser(byte[] b) {
    this(b, 0, b.length);
  }

  /**
   * 新しいデコンプレッサを作成します。
   *
   * @param b Packファイル内の1エントリ分のデータ領域のバイト列
   * @param off 入力データの開始オフセット
   * @param len 入力データの長さ
   */
  public LZSSDecompresser(byte[] b, int off, int len) {
    this(ByteBuffer.wrap(b, off, len));
  }

  /**
   * 新しいデコンプレッサを作成します。
   *
   * @param b Packファイル内の1エントリ分のデータ領域のバイトバッファ
   */
  public LZSSDecompresser(ByteBuffer buf) {
    this.dataBuf = buf.asReadOnlyBuffer();
    dataBuf.order(ByteOrder.LITTLE_ENDIAN);
    readDataHeader();

    this.rDataRemaining = dataBuf.remaining();
    this.wDataRemaining = originalSize;
  }

  private void readDataHeader() {
    compressedSize = dataBuf.getInt();
    originalSize = dataBuf.getInt();
    if (originalSize < compressedSize) {
      throw new IllegalArgumentException("invalid size");
    }
    bytesRead += 8;
  }

  /**
   * 圧縮されたエントリ・データのサイズを設定します。
   *
   * @return エントリ・データの圧縮時のサイズ
   */
  public int getCompressedSize() {
    return compressedSize;
  }

  /**
   * エントリ・データの圧縮解除時のサイズを設定します。
   *
   * @return エントリ・データの圧縮解除時のサイズ
   */
  public int getSize() {
    return originalSize;
  }

  /**
   * これまでに読み込んだ、圧縮されたバイトの総数を返します。
   *
   * @return これまでに読み込んだ、圧縮されたバイトの総数
   */
  public int getTotalIn() {
    ensureOpen();
    return bytesRead;
  }

  /**
   * これまでに出力された、圧縮解除されたバイトの総数を返します。
   *
   * @return これまでに出力された、圧縮解除されたバイトの総数
   */

  public int getTotalOut() {
    ensureOpen();
    return bytesWritten;
  }

  /**
   * 指定されたバッファにバイトを圧縮解除します。実際に圧縮解除されたバイト数を返します。
   *
   * @param b 圧縮解除されるデータ用のバッファ
   * @return 圧縮解除される実効バイト数
   */
  public int decompress(byte[] b) {
    return decompress(b, 0, b.length);
  }

  /**
   * 指定されたバッファにバイトを圧縮解除します。実際に圧縮解除されたバイト数を返します。
   *
   * @param b 圧縮解除されるデータ用のバッファ
   * @param off データの開始オフセット
   * @param len 圧縮解除される最大バイト数
   * @return 圧縮解除される実効バイト数
   */
  public int decompress(byte[] b, int off, int len) {
    Objects.requireNonNull(b);
    if (off < 0 || len < 0 || off > b.length - len) {
      throw new IndexOutOfBoundsException();
    } else if (len == 0) {
      return 0;
    }
    ensureOpen();

    if (wDataRemaining <= 0) {
      return -1;
    }

    int readLength = len;
    if (wDataRemaining <= len) {
      readLength = wDataRemaining;
    }

    int oldDataRemaining = rDataRemaining;
    int n = decompressBytes(b, off, readLength);
    if (n == -1) {
      return -1;
    }

    bytesRead += (oldDataRemaining - rDataRemaining);
    bytesWritten += n;
    return n;
  }

  private int decompressBytes(byte[] b, int off, int len) {
    int c = decompressByte();
    if (c == -1) {
      return -1;
    }

    b[off] = (byte) c;

    int i = 1;
    for (; i < len; i++) {
      c = decompressByte();
      if (c == -1) {
        break;
      }
      b[off + i] = (byte) c;
    }
    wDataRemaining -= i;
    return i;
  }

  private int decompressByte() {
    if (0 < referenceRemaining) {
      int referenceIndex = referenceLength - referenceRemaining;
      int ref = Byte.toUnsignedInt(referenceCache[referenceIndex]);
      referenceRemaining--;
      return ref;
    }
    if (blockDataRemaining == 0) {
      if (readFlag() == null) {
        return -1;
      }
      blockDataRemaining = BLOCKDATA_SIZE;
    }

    int blockDataIndex = BLOCKDATA_SIZE - blockDataRemaining;
    blockDataRemaining--;

    int r1 = readCompressedByte();
    if (r1 == -1) {
      return -1;
    }
    if (flag.get(blockDataIndex)) {
      readDirect(r1);
      return r1;
    }

    int r2 = readCompressedByte();
    if (r2 == -1) {
      return -1;
    }
    referenceLength = readUsingTable(r1, r2);
    referenceRemaining = referenceLength - 1;
    return Byte.toUnsignedInt(referenceCache[0]);
  }

  private BitSet readFlag() {
    int r;
    if ((r = readCompressedByte()) == -1) {
      return null;
    }

    for (int i = 0; i < BLOCKDATA_SIZE; i++) {
      flag.set(i, ((r >>> i) & 1) == 1);
    }
    return flag;
  }

  private int readDirect(int r) {
    dicTable[currDicPos] = (byte) r;
    currDicPos = (currDicPos + 1) & 0xFFF;
    return 1;
  }

  private int readUsingTable(int r1, int r2) {
    int position = r1 + (((r2 >>> 4) & 0xF) << 8);
    int length = (r2 & 0xF) + 3;
    for (int i = 0; i < length; i++) {
      byte d = dicTable[(position + i) & 0xFFF];
      dicTable[currDicPos] = d;
      currDicPos = (currDicPos + 1) & 0xFFF;
      referenceCache[i] = d;
    }
    return length;
  }

  private int readCompressedByte() {
    if (dataBuf.remaining() <= 0) {
      return -1;
    }
    int i = Byte.toUnsignedInt(dataBuf.get());
    rDataRemaining = dataBuf.remaining();
    return i;
  }

  /**
   * デコンプレッサを閉じ、圧縮解除された入力をすべて破棄します。
   *
   * @see java.io.Closeable#close()
   */
  @Override
  public void close() {
    dataBuf = null;
    closed = true;
  }

  /**
   * 現在の入力データのEOFに達したあとで呼び出した場合に0を返します。そうでない場合は常に1を返します。
   *
   * このメソッドは、ブロックなしで読み込める実際のバイト数を返すためのものではありません。
   *
   * @return EOFの前では1、EOFに達したあとでは0。
   * @throws IOException 入出力エラーが発生した場合
   *
   * @see java.io.InputStream#available()
   */
  public int available() throws IOException {
    ensureOpen();
    if (wDataRemaining <= 0) {
      return 0;
    }
    return 1;
  }

  private void ensureOpen() {
    if (closed) {
      throw new NullPointerException("Decompresser has been closed");
    }
  }
}
