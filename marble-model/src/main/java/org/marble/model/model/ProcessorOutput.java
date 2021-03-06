package org.marble.model.model;

public class ProcessorOutput {
    private Boolean valid = Boolean.TRUE;
    private Double polarity;
    private String message;
    private String notes;

    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    public Double getPolarity() {
        return polarity;
    }

    public void setPolarity(Double polarity) {
        this.polarity = polarity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "ProcessingOutput [polarity=" + polarity + ", notes=" + notes + "]";
    }
}
