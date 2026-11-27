/**
 * Telas do módulo SPDA na plataforma: cada uma leva o <DialogHost /> (avisos e
 * confirmações do módulo), que no app independente ficava na raiz do app, e
 * liga a sincronização das inspeções com o Supabase enquanto está aberta.
 */
import React, { useEffect } from 'react';
import { DialogHost } from './dialogs';
import { SyncBar } from './SyncBar';
import { startSpdaSync } from '../sync';
import { SpdaModuleView } from './SpdaModuleView';
import { DashboardView } from './DashboardView';
import { TechReportView } from './TechReportView';
import { SpdaBuildingsView } from './BuildingsView';
import { SpdaAterramentoView, SpdaCaptacaoView, SpdaDescidaView, SpdaDpsView } from './SubsystemView';
import { SpdaDataView } from './DataReportView';
import { SpdaSettingsView } from './SpdaSettingsView';

const withDialogs = (View: React.FC): React.FC => {
  const Screen: React.FC = () => {
    useEffect(() => startSpdaSync(), []);
    return (
      <div className="space-y-4">
        <SyncBar />
        <View />
        <DialogHost />
      </div>
    );
  };
  return Screen;
};

export const SpdaInspectionsScreen = withDialogs(SpdaModuleView);
export const SpdaDashboardScreen = withDialogs(DashboardView);
export const SpdaReportScreen = withDialogs(TechReportView);
export const SpdaBuildingsScreen = withDialogs(SpdaBuildingsView);
export const SpdaCaptacaoScreen = withDialogs(SpdaCaptacaoView);
export const SpdaDescidaScreen = withDialogs(SpdaDescidaView);
export const SpdaAterramentoScreen = withDialogs(SpdaAterramentoView);
export const SpdaDpsScreen = withDialogs(SpdaDpsView);
export const SpdaDataScreen = withDialogs(SpdaDataView);
export const SpdaSettingsScreen = withDialogs(SpdaSettingsView);
