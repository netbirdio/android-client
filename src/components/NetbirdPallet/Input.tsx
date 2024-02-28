import styled from 'styled-components';
import { Text, TextInput, TouchableOpacity, View } from 'react-native';

export const NbMaskTextInput = styled(View)`
  font-size: 14px;
  padding: 0px 8px;
  border: solid 1px rgba(217, 217, 217, 1);
  background-color: white;
  flex-direction: row;
  align-items: center;
  overflow: hidden;
`;

export const NbTextInput = styled(TextInput)`
  flex: 1;
  color: rgba(77, 77, 77, 1);
  height: 32px;
  padding: 0;
  font-family: 'Roboto';
  line-height: 24px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.35);
`;

export const NbClearInput = styled(TouchableOpacity)`
  padding: 3px 0 3px 3px;
`;

export const NbLabelError = styled(Text)`
  padding-top: 5px;
  margin-left: 8px;
  font-size: 12px;
`;
