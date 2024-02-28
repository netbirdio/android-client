import AsyncStorage from '@react-native-community/async-storage';
import React, {useEffect, useState} from 'react';
import {createContext} from 'react';

import {ThemeProvider as ThemeProviderStyled} from 'styled-components';
import {darkTheme} from './darkTheme';
import {lightTheme} from './lightTheme';

export enum ThemeType {
  light = 'light',
  dark = 'dark',
}

const themes = {
  [ThemeType.light]: lightTheme,
  [ThemeType.dark]: darkTheme,
};

export const ThemeContext = createContext({
  theme: ThemeType.light,
  toggleTheme: () => {},
});

export const ThemeProvider: React.FC<any> = ({children}) => {
  const [theme, setTheme] = useState(ThemeType.dark);

  useEffect(() => {
    loadTheme();
  }, []);

  async function loadTheme() {
    //Todo: implements device theme when use device style default
    const savedTheme: any = await AsyncStorage.getItem('@theme');
    if (savedTheme) {
      setTheme(savedTheme);
    }
  }

  function toggleTheme() {
    let newTheme;
    if (theme === ThemeType.light) {
      newTheme = ThemeType.dark;
    } else {
      newTheme = ThemeType.light;
    }

    AsyncStorage.setItem('@theme', newTheme);
    setTheme(newTheme);
  }

  return (
    <ThemeContext.Provider value={{theme, toggleTheme}}>
      <ThemeProviderStyled theme={themes[theme]}>
        {children}
      </ThemeProviderStyled>
    </ThemeContext.Provider>
  );
};
