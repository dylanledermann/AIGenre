import { Link } from "react-router-dom";

const Navbar = () => {
  return(
    <div style={{ 
        width: '100%', 
        height: '7.5vh',
        background: 'var(--color-bg-muted)'
    }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        padding: '1% 0 1% 1%',
        background: 'var(--color-bg-muted)'
      }}>
        <h2><Link to="/" style={{textDecoration: "none"}}>Genre Guesser</Link></h2>
      </div>
    </div>
  );
};

export default Navbar;
