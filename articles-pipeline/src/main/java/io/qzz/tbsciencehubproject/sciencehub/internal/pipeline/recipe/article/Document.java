package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article;

import io.qzz.tbsciencehubproject.user.User;
import java.util.Collection;

public interface Document {

  String filename();

  Collection<User> signatures();

  void sign(User user);

  boolean hasSigned(User user);

  boolean hasSignatures(User user);

  byte[] contents();

  boolean verify();

}
