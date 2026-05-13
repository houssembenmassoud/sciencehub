package io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe;
import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.article.Document;

import io.qzz.tbsciencehubproject.sciencehub.internal.pipeline.recipe.service.SignatureService;
import io.qzz.tbsciencehubproject.user.User;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Concrete implementation of Document interface with signing capability.
 * Integrates with existing User interface - no duplication.
 */
public class DocumentImpl implements Document {
    
    private final String filename;
    private final byte[] contents;
    private final String articleId;
    private final SignatureService signatureService;
    
    public DocumentImpl(String filename, byte[] contents, String articleId, 
                       SignatureService signatureService) {
        this.filename = filename;
        this.contents = contents;
        this.articleId = articleId;
        this.signatureService = signatureService;
    }
    
    @Override
    public String filename() {
        return filename;
    }
    
    @Override
    public Collection<User> signatures() {
        // Retrieve signature metadata and convert to User representations
        return signatureService.getSignatures(articleId)
            .stream()
            .map(sig -> (User) () -> sig.getUserId())  // Simple User implementation
            .collect(Collectors.toList());
    }
    
    @Override
    public void sign(User user) {
        signatureService.signDocument(articleId, user, contents, "Article approval");
    }
    
    @Override
    public boolean hasSigned(User user) {
        return signatureService.getSignatures(articleId)
            .stream()
            .anyMatch(sig -> sig.getUserId().equals(user.name()));
    }
    
    @Override
    public boolean hasSignatures(User user) {
        return hasSigned(user);
    }
    
    @Override
    public byte[] contents() {
        return contents;
    }
    
    /**
     * Verify document integrity
     */
    public boolean verify() {
        return signatureService.verifySignatures(articleId, contents);
    }
}
