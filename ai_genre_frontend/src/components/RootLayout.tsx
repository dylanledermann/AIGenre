import { Suspense } from 'react';
import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';

const RootLayout = () => {
  return (
    <>
      <nav>
        <Navbar />
      </nav>
      <main>
        <Suspense fallback={<div>Loading...</div>}>
          <Outlet />
        </Suspense>
      </main>
    </>
  );
};

export default RootLayout;
