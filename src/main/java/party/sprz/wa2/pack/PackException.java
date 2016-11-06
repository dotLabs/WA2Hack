package party.sprz.wa2.pack;

import java.io.IOException;

/**
 * ソートのPack例外が発生したことを通知します。
 *
 * @author Nan
 *
 */
public class PackException extends IOException {
  private static final long serialVersionUID = 4940755595804771097L;

  /**
   * エラー詳細メッセージとしてnullを設定してPackExceptionを構築します。
   */
  public PackException() {
    super();
  }

  /**
   * 指定された詳細メッセージを持つPackExceptionを構築します。
   *
   * @param s 詳細メッセージ。
   */
  public PackException(String s) {
    super(s);
  }
}
