module.exports = {
  presets: ['module:metro-react-native-babel-preset', '@babel/preset-typescript'],
  plugins: [
    [
      'module-resolver',
      {
        root: ['./'],
        extensions: ['.ts', '.d.ts', '.tsx', '.jsx', '.js', '.json'],
        alias: {
          '@assets': './src/assets',
          '@components': './src/components',
          '@navigation': './src/navigation',
          '@screens': './src/screens',
          '@utils': './src/utils',
          '@src': './src',
        },
      },
    ],
    'react-native-reanimated/plugin',
  ],
};
