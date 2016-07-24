package party.sprz.wa2.pack;

import java.nio.charset.Charset;

public interface PackConstants {
  int PACKHEADER_SIZE = 16;
  int SIGNATURE_OFF = 0;
  int SIGNATURE_SIZE = 4;
  int ENTRYCOUNT_OFF = 12;

  int KCAPHEADER_SIZE = 44;
  int METHOD_OFF = 0;
  int ENTRYNAME_OFF = 4;
  int ENTRYNAME_SIZE = 24;
  int OFFSET_OFF = 36;
  int K_COMPSIZE_OFF = 40;

  int DATA_HEADER_SIZE = 8;
  int D_COMPSIZE_OFF = 0;
  int SIZE_OFF = 4;

  int BLOCKDATA_SIZE = 8;

  String KCAP_SIGNATURE = "KCAP";
  Charset PACK_ENCODING = Charset.forName("Windows-31J");
}
