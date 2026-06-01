import React, { useRef, useState } from 'react';
import { AiOutlineCheckCircle, AiOutlineCloudUpload } from 'react-icons/ai';
import { MdClear } from 'react-icons/md';
import styles from './DragNDrop.module.css';

type DragNDropProps = {
  file: File | null;
  setFile: (file: File | null) => void;
  setError: (error: string | null) => void;
};

const ACCEPTED_MIME_REGEX =
  /.((x-)?mp3|(x-)?wav|mpeg|ogg|(x\\-)?flac|x\\-m4a|mp4a-latm|aac|(x\\-)?aiff)$/;

const ACCEPTED_EXTENSIONS =
  '.mp3,.x-mp3,.wav,.x-wav,.mpeg,.ogg,.x-ogg,.flac,.x-flac,.m4a,.x-m4a,.aac,.aiff,.x-aiff';

const DragNDrop = ({ file, setFile, setError }: DragNDropProps) => {
  const [isDragging, setIsDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const validateAndSet = (incoming: File) => {
    if (!ACCEPTED_MIME_REGEX.test(incoming.name)) {
      setError('Invalid file type — please upload an audio file.');
      return;
    }

    // Set the file and reset the ref (Allows for re-adding the same image on upload/removal).
    setFile(incoming);
    if (inputRef.current) inputRef.current.value = '';
    setError(null);
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);

    const droppedFiles = e.dataTransfer.files;
    if (!droppedFiles.length) {
      setError('File required.');
      return;
    }
    validateAndSet(droppedFiles[0]);
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => setIsDragging(false);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files;
    if (selected && selected.length > 0) {
      validateAndSet(selected[0]);
    }
  };

  const handleRemoveFile = () => {
    setError(null);
    setFile(null);
    // Reset the input so the same file can be re-selected
    if (inputRef.current) inputRef.current.value = '';
  };

  return (
    <section className={styles.wrapper}>
      <input
        ref={inputRef}
        type="file"
        id="browse"
        hidden
        onChange={handleFileChange}
        accept={ACCEPTED_EXTENSIONS}
      />

      <div
        className={`${styles.dropzone} ${isDragging ? styles.dragging : ''} ${file ? styles.hasFile : ''}`}
        onDrop={handleDrop}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
      >
        {/* Upload prompt */}
        <div className={`${styles.prompt} ${file ? styles.promptHidden : ''}`}>
          <div className={styles.iconWrap}>
            <AiOutlineCloudUpload className={styles.uploadIcon} />
          </div>
          <p className={styles.promptTitle}>Drop your audio file here</p>
          <p className={styles.promptSub}>
            Supported formats: WAV · MP3 · OGG · FLAC · M4A · AAC · AIFF
          </p>
          <label htmlFor="browse" className={styles.browseBtn}>
            Browse files
          </label>
        </div>

        {/* File pill */}
        {file && (
          <div className={styles.fileRow}>
            <div className={styles.filePill}>
              <AiOutlineCheckCircle className={styles.checkIcon} />
              <span className={styles.fileName}>{file.name}</span>
              <span className={styles.fileSize}>{(file.size / 1024 / 1024).toFixed(2)} MB</span>
              <button
                className={styles.removeBtn}
                onClick={handleRemoveFile}
                aria-label="Remove file"
                type="button"
              >
                <MdClear />
              </button>
            </div>
            <label htmlFor="browse" className={styles.replaceBtn}>
              Replace file
            </label>
          </div>
        )}
      </div>
    </section>
  );
};

export default DragNDrop;
