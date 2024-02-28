import styled from 'styled-components';
import {Text, View} from 'react-native';

export const NbFormArea = styled(View)`
  flex: 1;
  padding: 24px 48px;
`;

export const NbFormContent = styled(View)`
  margin-bottom: 15px;
`;

export const NbFormTextLabel = styled(Text)`
  margin-bottom: 5px;
  font-size: 14px;
  font-weight: 400;
`;

export const NbFormInputGroup = styled(View)`
  margin-bottom: 10px;
`;

export const NbButtonGroup = styled(View)`
  flex: 1;
  gap: 25px;
`;
