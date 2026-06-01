package dylanlederman.ai_genre.models;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public sealed interface ResultModel 
    permits ResultModel.Pending,
        ResultModel.Processing,
        ResultModel.Complete,
        ResultModel.Failed 
{
    @NotNull
    UUID taskId();
    @NotNull @Size(min=64, max=64)
    String fileHash();
    String status();

    public Set<String> validate();

    record Pending(UUID taskId, String fileHash, String status) implements ResultModel {
        // Ensures the status is pending
        public Pending { status = "PENDING"; }
        // Allows for creating the ResultModel without including the status
        public Pending(UUID taskId, String fileHash) { this(taskId, fileHash, "PENDING"); }

        public Set<String> validate() {
            Set<String> resultViolations = new HashSet<String>();
            if (taskId == null) {
                resultViolations.add("Task Id cannot be null");
            } if (fileHash == null) {
                resultViolations.add("File Hash cannot be null");
            } else if (fileHash.length() != 64) {
                resultViolations.add("Task Id must be a valid SHA256 string");
            }

            return resultViolations;
        }
    }
    record Processing(UUID taskId, String fileHash, String status) implements ResultModel {
        // Ensures the status is pending
        public Processing { status = "PROCESSING"; }
        // Allows for creating the ResultModel without including the status
        public Processing(UUID taskId, String fileHash) { this(taskId, fileHash, "PROCESSING"); }

        public Set<String> validate() {
            Set<String> resultViolations = new HashSet<String>();
            if (taskId == null) {
                resultViolations.add("Task Id cannot be null");
            } if (fileHash == null) {
                resultViolations.add("File Hash cannot be null");
            } else if (fileHash.length() != 64) {
                resultViolations.add("Task Id must be a valid SHA256 string");
            }

            return resultViolations;
        }
    }
    record Complete(UUID taskId, String fileHash, String status, GenreResultModel result) implements ResultModel {
        // Ensures the status is pending
        public Complete { status = "COMPLETE"; }
        // Allows for creating the ResultModel without including the status
        public Complete(UUID taskId, String fileHash, GenreResultModel result) { this(taskId, fileHash, "COMPLETE", result); }

        public Set<String> validate() {
            Set<String> resultViolations = new HashSet<String>();
            if (taskId == null) {
                resultViolations.add("Task Id cannot be null");
            } if (fileHash == null) {
                resultViolations.add("File Hash cannot be null");
            } else if (fileHash.length() != 64) {
                resultViolations.add("Task Id must be a valid SHA256 string");
            } if (result == null) {
                resultViolations.add("Result cannot be null");
            }

            return resultViolations;
        }
    }
    record Failed(UUID taskId, String fileHash, String status, String error) implements ResultModel {
        // Ensures the status is pending
        public Failed { status = "FAILED"; }
        // Allows for creating the ResultModel without including the status
        public Failed(UUID taskId, String fileHash, String error) { this(taskId, fileHash, "FAILED", error); }

        public Set<String> validate() {
            Set<String> resultViolations = new HashSet<String>();
            if (taskId == null) {
                resultViolations.add("Task Id cannot be null");
            } if (fileHash == null) {
                resultViolations.add("File Hash cannot be null");
            } else if (fileHash.length() != 64) {
                resultViolations.add("Task Id must be a valid SHA256 string");
            } if (error == null) {
                resultViolations.add("Error cannot be null");
            }

            return resultViolations;
        }
    }
}
