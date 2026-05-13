package io.qzz.tbsciencehubproject.pipeline.validate;

public class PipelineValidationException extends Exception {
    public PipelineValidationException(String message) {
        super(message);
    }
    
    public PipelineValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
