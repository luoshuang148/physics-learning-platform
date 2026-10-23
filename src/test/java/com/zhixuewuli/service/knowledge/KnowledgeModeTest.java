package com.zhixuewuli.service.knowledge;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class KnowledgeModeTest {
 @Test void mapsApplicationModesToTheSelectedRetrievalBranch(){
  assertThat(KnowledgeMode.OFF.retrievalMode()).isNull();
  assertThat(KnowledgeMode.VECTOR.retrievalMode()).isEqualTo(RetrievalMode.VECTOR);
  assertThat(KnowledgeMode.AUTO.retrievalMode()).isEqualTo(RetrievalMode.HYBRID);
 }
}
