import { Link } from 'react-router-dom';
import { useIsMobile } from '../hooks/useIsMobile';

const Home = () => {
  const isMobile = useIsMobile();

  return (
    <div
      style={{ display: 'grid', gap: '5%', gridTemplateColumns: `${isMobile ? '1fr' : '1fr 1fr'}` }}
    >
      <div style={{ margin: '5%' }}>
        <h4>Welcome to Genre Guesser.</h4>
        <div style={{ margin: `${isMobile ? '0' : '5%'}` }}>
          This project uses ResNet-18 model finetuned using the{' '}
          <a href="https://www.kaggle.com/datasets/imsparsh/fma-free-music-archive-small-medium/data">
            FMA music dataset
          </a>{' '}
          to guess the genre of songs. Specific information about the project can be found in the{' '}
          <a href="https://github.com/dylanledermann/AIGenre">Github</a>. To start guessing{' '}
          <Link to="/dashboard">Click Here</Link>.
        </div>
      </div>
      <div style={{ minWidth: '170px', margin: '5%' }}>
        Possible predictions consists of 16 genres:
        <ul>
          <li>Blues</li>
          <li>Classical</li>
          <li>Country</li>
          <li>Easy Listening</li>
          <li>Electronic</li>
          <li>Experimental</li>
          <li>Folk</li>
          <li>Hip-Hop</li>
          <li>Instrumental</li>
          <li>International</li>
          <li>Jazz</li>
          <li>Old-Time / Historic</li>
          <li>Pop</li>
          <li>Rock</li>
          <li>Soul-RnB</li>
          <li>Spoken</li>
        </ul>
      </div>
    </div>
  );
};

export default Home;
