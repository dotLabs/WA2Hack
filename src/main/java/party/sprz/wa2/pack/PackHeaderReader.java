package party.sprz.wa2.pack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * このクラスは、Packファイル形式のヘッダー及びエントリヘッダーを読み込みます。
 *
 * 利用時にはバイトの読み込み用にreadを実装してください
 *
 * @author Nan
 *
 */
public abstract class PackHeaderReader implements PackConstants {
  /**
   * Packファイル形式のヘッダー部分を読み込みます
   *
   * 現在はKCAP形式のデータのみをサポートしています。
   *
   * @return Packファイル内のエントリー数
   * @throws PackException - Packファイル・エラーが発生した場合
   * @throws IOException 入出力エラーが発生した場合
   */
  protected int readHeader() throws PackException, IOException {
    byte[] b = new byte[PACKHEADER_SIZE];
    if (read(b) != b.length) {
      throw new PackException("KCAP Header is broken (header size does'nt match)");
    }

    String signature = new String(b, SIGNATURE_OFF, SIGNATURE_SIZE, PACK_ENCODING);
    if (!KCAP_SIGNATURE.equals(signature)) {
      throw new PackException("Unsupported archive (might be LAC archive or WMV video)");
    }

    int entryCount = get32(b, ENTRYCOUNT_OFF);
    if (entryCount < 0) {
      throw new PackException("KCAP Header is broken (invalid entry count)");
    }
    return entryCount;
  }

  /**
   * Packファイル形式のエントリのヘッダー部分を読み込みます
   *
   * 読み込んだヘッダーには圧縮解除時のサイズが含まれていないため、後操作で追加してください
   *
   * @return 読み込んだPackファイル・エントリ
   * @throws PackException - Packファイル・エラーが発生した場合
   * @throws IOException 入出力エラーが発生した場合
   */
  protected PackEntry readEntryHeader() throws PackException, IOException {
    PackEntry e;

    byte[] b = new byte[KCAPHEADER_SIZE];
    if (read(b) != b.length) {
      throw new PackException("Entry Header is broken (header size does'nt match)");
    }

    int compressedFlag = get32(b, METHOD_OFF);
    String filename = new String(b, ENTRYNAME_OFF, ENTRYNAME_SIZE, PACK_ENCODING).trim();
    int offset = get32(b, OFFSET_OFF);
    int length = get32(b, K_COMPSIZE_OFF);

    e = new PackEntry(filename);
    e.setMethod(compressedFlag);
    e.setOffset(offset);
    e.setCompressedSize(length);
    return e;
  }

  /**
   * ヘッダー読み込みに利用するバイトを読み込みます。各部で実装してください
   *
   * @param b
   * @return
   * @throws IOException 入出力エラーが発生した場合
   */
  abstract protected int read(byte[] b) throws IOException;

  private int get32(byte[] b, int off) {
    return ByteBuffer.wrap(b, off, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
  }
}
