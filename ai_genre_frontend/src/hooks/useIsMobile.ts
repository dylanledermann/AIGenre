import { useState, useCallback, useEffect } from 'react';

export const useIsMobile = (breakpoint = 600): boolean => {
  const [isMobile, setIsMobile] = useState<boolean>(window.innerWidth <= breakpoint);

  const onResize = useCallback(() => {
    setIsMobile(window.innerWidth <= breakpoint);
  }, [breakpoint]);

  useEffect(() => {
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, [onResize]);

  return isMobile;
};
