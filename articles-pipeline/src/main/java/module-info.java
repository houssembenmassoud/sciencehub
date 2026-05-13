module io.qzz.tbsciencehubproject.sciencehub.core_impl {
  requires io.qzz.tbsciencehubproject.sciencehub.core;

  // Spring Framework
  requires spring.context;
  requires spring.tx;
  requires spring.data.jpa;
  requires spring.data.commons;

  // Jakarta EE
  requires jakarta.persistence;
  requires jakarta.annotation;

  // Hibernate
  requires org.hibernate.orm.core;

  // BouncyCastle
  requires org.bouncycastle.provider;
  requires org.bouncycastle.pkix;
}