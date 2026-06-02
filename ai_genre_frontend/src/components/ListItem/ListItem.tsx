import type { WebsocketState } from '../../types/WebsocketTypes/WebsocketTypes';
import styles from './ListItem.module.css';

type ListItemProps = {
  taskId: string;
  connection: WebsocketState;
};

const getProgressPercentage = (statusStep: number): number => {
  return (Math.min(statusStep, 3) / 3) * 100;
};

const ListItem = ({ taskId, connection }: ListItemProps) => {
  const { status, error, results } = connection;

  return (
    <div
      className={styles.item}
      data-status={status.name}
      role="listitem"
      aria-label={`Task ${taskId} - ${status.name}`}
      style={{ margin: '1% 0' }}
    >
      {/* Header */}
      <div className={styles.header}>
        <span className={styles.taskId}>
          Task <span className={styles.taskIdValue}>#{taskId}</span>
        </span>

        <span className={styles.badge}>
          <span className={styles.dot} aria-hidden="true" />
          {status.name}
        </span>
      </div>

      {/* Results/Error */}
      <div className={styles.body}>
        {error && (
          <span className={styles.error} role="alert">
            {error}
          </span>
        )}
        {!error && results && (
          <span className={styles.result}>
            Genre: {results.genre}, Confidence: {parseFloat(results.accuracy).toFixed(2)}
          </span>
        )}
      </div>

      {/* Progress Bar  */}
      <div className={styles.footer}>
        <div className={styles.progressWrap}>
          <div
            className={styles.progressTrack}
            role="progressbar"
            aria-valuenow={getProgressPercentage(status.value)}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`${taskId} progress`}
          >
            <div
              className={styles.progressFill}
              style={{ width: `${getProgressPercentage(status.value)}%` }}
            />
          </div>

          <span className={styles.progressStatus}>{status.name}</span>
        </div>
      </div>
    </div>
  );
};

export default ListItem;
