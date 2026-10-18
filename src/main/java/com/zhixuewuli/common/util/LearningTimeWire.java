package com.zhixuewuli.common.util;
import java.time.*;
import java.time.format.DateTimeFormatter;
/** New learning times are UTC; legacy clocks retain their original, unknown local basis. */
public final class LearningTimeWire {
 private LearningTimeWire() {}
 public static String format(LocalDateTime value, boolean utc) {
  if(value==null)return null;
  return utc ? value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
             : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
 }
}
