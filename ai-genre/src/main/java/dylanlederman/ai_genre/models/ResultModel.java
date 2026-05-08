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

    public Set<String> validate();

    record Pending(UUID taskId, String fileHash) implements ResultModel {
        public String status() { return "PENDING"; }

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
    record Processing(UUID taskId, String fileHash) implements ResultModel {
        public String status() { return "PROCESSING"; }

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
    record Complete(UUID taskId, String fileHash, GenreResultModel result) implements ResultModel {
        public String status() { return "COMPLETE"; }

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
    record Failed(UUID taskId, String fileHash, String error) implements ResultModel {
        public String status() { return "FAILED"; }

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
