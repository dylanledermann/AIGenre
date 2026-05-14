const Dashboard = () => {

  const uploadFile = () => {

  };

  const connectWebsocket = () => {

  };
  
  return (
    <div style={{display: 'flex', flexWrap: 'wrap', justifySelf: 'center', margin: '2% 0', padding: '0 2%', maxWidth: '1000px'}}>
      <div style={{flexBasis: '100%', marginBottom: '2%'}}>
        <h3>
          Your Uploads
        </h3>
      </div>
      <p>
        Welcome to your dashboard. 
        Here you can upload songs to be guessed. 
        When you upload, the file may take a bit of time to load, but you will be able to see what stage you are on while uploading. 
        You can do multiple at a time, but they will all be separate uploads. 
        Uploads you make are not persistant, so please do not leave the page, or you will lose any progress for current uploads.
        If any problems occur, please notify via the <a href='https://github.com/dylanledermann/AIGenre'>Github</a>.
      </p>
      <div style={{flexBasis: '100%', borderBottom: '1px solid gray', margin: '1% 0', height: '0px'}}></div>
      <div style={{background: 'var(--color-bg-subtle)', border: '1px solid var(--color-bg-muted)', width: '100%', maxHeight: '60vh', height: '100vh', borderRadius: '20px'}}>
        <div style={{margin: '10px'}}>
          {/* { list the tasks here} */}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
