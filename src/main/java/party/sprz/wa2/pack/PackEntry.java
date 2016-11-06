package party.sprz.wa2.pack;

import java.util.Objects;

/**
 * このクラスはPackファイル・エントリを表すために使用されます。
 *
 * @author Nan
 *
 */
public class PackEntry implements PackConstants, Cloneable {
  private String name;
  private int method = -1; // 0:Uncompress 1:KCAP
  @SuppressWarnings("unused")
  private int unknown1;
  @SuppressWarnings("unused")
  private int unknown2;
  private int offset = -1;
  private int compressedSize = -1;
  private int originalSize = -1;

  /**
   * 指定された名前の新しいPackエントリを作成します。
   *
   * @param name エントリ名
   *
   * @throws NullPointerException エントリの名前がnullである場合
   * @throws IllegalArgumentException エントリの名前が0xFFFFバイトよりも長い場合
   */
  public PackEntry(String name) {
    Objects.requireNonNull(name, "name");
    if (name.length() > 0xFFFF) {
      throw new IllegalArgumentException("entry name too long");
    }
    this.name = name;
  }

  /**
   * エントリの名前を返します。
   *
   * @return エントリの名前
   */
  public String getName() {
    return name;
  }

  /**
   * エントリ・データの圧縮解除時のサイズを設定します。
   *
   * @param size 圧縮解除時のサイズ(バイト)
   *
   * @throws IllegalArgumentException 指定されたサイズが0未満の場合
   */
  public void setSize(int size) {
    if (size < 0) {
      throw new IllegalArgumentException("invalid entry size");
    }
    this.originalSize = size;
  }

  /**
   * エントリ・データの圧縮解除時のサイズを返します。
   *
   * @return エントリ・データの圧縮解除時のサイズ。不明の場合は -1
   */
  public int getSize() {
    return originalSize;
  }

  /**
   * 圧縮されたエントリ・データのサイズを返します。
   *
   * 格納されたエントリの場合、圧縮時のサイズは圧縮解除時のサイズと同じになります。
   *
   * @return エントリ・データの圧縮時のサイズ。不明の場合は -1
   */
  public int getCompressedSize() {
    return compressedSize;
  }

  /**
   * 圧縮されたエントリ・データのサイズを設定します。
   *
   * @param csize 設定される圧縮されたサイズ
   */
  public void setCompressedSize(int csize) {
    this.compressedSize = csize;
  }

  /**
   * エントリの圧縮メソッドを設定します。
   *
   * @param method 圧縮メソッド
   */
  public void setMethod(int method) {
    this.method = method;
  }

  /**
   * エントリの圧縮メソッドを返します。
   *
   * @return エントリの圧縮メソッド。指定されていない場合は -1
   */
  public int getMethod() {
    return method;
  }

  /**
   * エントリのオフセットを設定します。
   *
   * @param offset オフセット
   */
  public void setOffset(int offset) {
    if (offset < 0) {
      throw new IllegalArgumentException("invalid offset");
    }
    this.offset = offset;
  }

  /**
   * エントリのオフセットを返します。
   *
   * @param offset エントリのオフセット。指定されていない場合は -1
   */
  public int getOffset() {
    return offset;
  }
}
