import { useState } from 'react';
import ListItem from '../components/ListItem/ListItem';
import { useMutation } from '@tanstack/react-query';
import { createQuery } from '../services/InferenceService';
import config from '../config';
import DragNDrop from '../components/DragNDrop/DragNDrop';
import { useWebsockets } from '../hooks/WebsocketHook/WebsocketContext';
import { WebsocketStatuses } from '../types/WebsocketTypes/WebsocketTypes';

const Dashboard = () => {
  const { calls, connections, add, open } = useWebsockets();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);

  const queryMutation = useMutation({
    mutationFn: (file: File) => createQuery(file),
    onSuccess: (data) => {
      // reset file and error
      setFile(null);
      setError(null);
      if (data.status === WebsocketStatuses.PENDING.name || data.status === WebsocketStatuses.PROCESSING.name) {
        open(data.taskId, `${config.api.websocketTopic}/${data.taskId}`);
      } else {
        add(data);
      }
    },
  });

  const uploadFile = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    if (!file) {
      setError('File Required');
      return;
    }
    queryMutation.mutate(file);
  };

  return (
    <div
      style={{
        flexWrap: 'wrap',
        justifySelf: 'center',
        margin: '2% 0',
        padding: '0 2%',
        maxWidth: '1000px',
      }}
    >
      {/* Upload */}

      <div style={{ flexBasis: '100%', marginBottom: '2%' }}>
        <h3>Start An Upload</h3>
      </div>
      <DragNDrop file={file} setFile={setFile} setError={setError} />
      <div style={{ display: 'flex', justifyContent: 'end', flexWrap: 'wrap' }}>
        {queryMutation.isPending ? (
          <button style={{ width: '100%', maxWidth: '150px' }} onClick={uploadFile} disabled>
            Loading
          </button>
        ) : (
          <button
            style={{ width: '100%', maxWidth: '150px', color: `${error ? 'primary' : ''}` }}
            onClick={uploadFile}
            disabled={error !== null && file !== null}
            id='upload'
          >
            Upload
          </button>
        )}
        {error && <div style={{ textAlign: 'end', flexBasis: '100%', color: 'red' }}>{error}</div>}
        {queryMutation.error && (
          <div style={{ textAlign: 'end', flexBasis: '100%', color: 'orange' }}>
            {String(queryMutation.error)}
          </div>
        )}
      </div>

      {/* Past Uploads */}

      <div style={{ marginBottom: '2%' }}>
        <h3>Your Uploads</h3>
      </div>
      <p>
        Welcome to your dashboard. Here you can upload songs to be guessed. When you upload, the
        file may take a bit of time to load, but you will be able to see what stage you are on while
        uploading. You can do multiple at a time, but they will all be separate uploads. Uploads you
        make are not persistant, so please do not leave the page, or you will lose any progress for
        current uploads. If any problems occur, please notify via the{' '}
        <a href="https://github.com/dylanledermann/AIGenre">Github</a>.
      </p>
      <div
        style={{ flexBasis: '100%', borderBottom: '1px solid gray', margin: '1% 0', height: '0px' }}
      ></div>
      <div
        style={{
          background: 'var(--color-bg-subtle)',
          border: '1px solid var(--color-bg-muted)',
          width: '100%',
          maxHeight: '60vh',
          height: '100vh',
          borderRadius: '20px',
          overflow: 'scroll',
        }}
      >
        <div style={{ margin: '10px' }}>
          {/* { list the tasks here} */}
          {calls.map((taskId, index) => {
            return <ListItem key={index} taskId={taskId} connection={connections.get(taskId)!} />;
          })}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
