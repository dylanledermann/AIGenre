import config from '../config/index.ts';

export const createQuery = async (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  const response: Response = await fetch(config.api.baseUrl + '/query', {
    method: 'POST',
    body: formData,
  });
  if (!response.ok) throw new Error(`${response.status}`);
  return await response.json();
};
