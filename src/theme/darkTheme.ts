import {lightTheme} from './lightTheme';
export const darkTheme: typeof lightTheme = {
  colors: {
    default: 'rgba(246, 131, 48, 1)',
    background: '#FFF',
    backgroundSecondary: '#EEE',
    onBackground: '#121212',
    primary: '#ffffff',
    textDefault: 'rgba(77, 77, 77, 1)',
    textDescription: 'rgba(0, 0, 0, .35)',
    textModalDescription: 'rgba(0, 0, 0, 0.85)',
  },
  text: {
    sizeDefault: '14px',
  },
  space: {
    default: 16,
  },
  form: {
    input: {
      clearIcon: {
        color: 'rgba(0, 0, 0, .35)',
      },
    },
  },
};
