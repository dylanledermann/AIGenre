import { RouterProvider } from 'react-router-dom';
import { lazy } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import RootLayout from './components/RootLayout.tsx';

const Dashboard = lazy(() => import('./pages/Dashboard.tsx'));
const Home = lazy(() => import('./pages/Home.tsx'));
const NotFound = lazy(() => import('./pages/NotFound.tsx'));

const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    errorElement: <div>Something went wrong</div>,
    children: [
      { index: true, element: <Home /> },
      { path: 'dashboard', element: <Dashboard /> },
      { path: '*', element: <NotFound /> },
    ],
  },
]);

const App = () => {
  return <RouterProvider router={router} />;
};

export default App;
