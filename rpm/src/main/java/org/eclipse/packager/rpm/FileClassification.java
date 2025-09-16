package org.eclipse.packager.rpm;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class FileClassification {
    private String fileType;

    private String fileMime;

    private String fileName;

    private Set<FileColor> fileColors;

    public FileClassification() {
        this.fileColors = EnumSet.noneOf(FileColor.class);
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(final String fileType) {
        this.fileType = fileType;
    }

    public String getFileMime() {
        return fileMime;
    }

    public void setFileMime(final String fileMime) {
        this.fileMime = fileMime;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    public Set<FileColor> getFileColors() {
        return fileColors;
    }

    public void setFileColors(final Set<FileColor> fileColors) {
        this.fileColors.addAll(fileColors);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final FileClassification that = (FileClassification) o;
        return Objects.equals(fileType, that.fileType) && Objects.equals(fileMime, that.fileMime) && Objects.equals(fileName, that.fileName) && fileColors == that.fileColors;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileType, fileMime, fileName, fileColors);
    }

    @Override
    public String toString() {
        return "RpmFileColor{" +
            "ftype='" + fileType + '\'' +
            ", fmime='" + fileMime + '\'' +
            ", fn='" + fileName + '\'' +
            ", fcolor=" + fileColors +
            '}';
    }
}
