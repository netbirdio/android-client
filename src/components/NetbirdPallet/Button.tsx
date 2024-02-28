import styled from 'styled-components';
import {Text, TouchableOpacity, View} from 'react-native';

export const NbTouchableOpacity = styled(TouchableOpacity)`
  border: 1px solid #f35e32;
  border-radius: 2px;
  padding: 8px 15px;
  align-self: stretch;
  align-items: center;
  justify-items: center;
  background-color: #f68330;
`;

export const NbTextTouchableOpacity = styled(Text)`
  font-size: 14px;
  line-height: 22px;
  color: #ffffff;
`;

export const NbTouchableOpacityOutline = styled(NbTouchableOpacity)`
  background-color: transparent;
  border-color: #f35e32;
`;

export const NbTextTouchableOpacityOutline = styled(NbTextTouchableOpacity)`
  color: #f68330;
`;

export const NbContent = styled(View)`
  flex-direction: row;
  align-items: center;
`;
