import React, { Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { MainLayout } from '@/layouts';
import { AuthGuard } from '@/features/auth/AuthGuard';
import { Login } from '@/pages/Login';
import { Landing } from '@/pages/Landing';

// Lazy-load heavy pages to optimize initial bundle size & Raspberry Pi performance
const Dashboard = React.lazy(() => import('@/pages/Dashboard').then(m => ({ default: m.Dashboard })));
const Threats = React.lazy(() => import('@/pages/Threats').then(m => ({ default: m.Threats })));
const Devices = React.lazy(() => import('@/pages/Devices').then(m => ({ default: m.Devices })));
const DeviceDetail = React.lazy(() => import('@/pages/DeviceDetail').then(m => ({ default: m.DeviceDetail })));
const Watch = React.lazy(() => import('@/pages/Watch').then(m => ({ default: m.Watch })));
const Recovery = React.lazy(() => import('@/pages/Recovery').then(m => ({ default: m.Recovery })));
const Reports = React.lazy(() => import('@/pages/Reports').then(m => ({ default: m.Reports })));
const Settings = React.lazy(() => import('@/pages/Settings').then(m => ({ default: m.Settings })));
const Attacks = React.lazy(() => import('@/pages/Attacks').then(m => ({ default: m.Attacks })));
const About = React.lazy(() => import('@/pages/About').then(m => ({ default: m.About })));
const AssistantPage = React.lazy(() => import('@/features/assistant/AssistantPage').then(m => ({ default: m.AssistantPage })));

function RouteLoadingFallback() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] space-y-4">
      <div className="relative w-12 h-12">
        <div className="absolute inset-0 rounded-full border-2 border-primary/20 border-t-primary animate-spin" />
        <div className="absolute inset-2 rounded-full border-2 border-cyan-500/20 border-b-cyan-500 animate-spin [animation-direction:reverse]" />
      </div>
      <p className="text-xs uppercase tracking-widest text-muted-foreground font-mono">Loading module...</p>
    </div>
  );
}

export function AppRoutes() {
  return (
    <Suspense fallback={<RouteLoadingFallback />}>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        
        <Route element={<AuthGuard />}>
          <Route element={<MainLayout />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/threats" element={<Threats />} />
            <Route path="/devices" element={<Devices />} />
            <Route path="/devices/:id" element={<DeviceDetail />} />
            <Route path="/watch" element={<Watch />} />
            <Route path="/recovery" element={<Recovery />} />
            <Route path="/analytics" element={<Reports />} />
            <Route path="/reports" element={<Reports />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/simulation" element={<Attacks />} />
            <Route path="/ai-assistant" element={<AssistantPage isGlobalMode={false} />} />
            <Route path="/docs" element={<Navigate to="/reports" replace />} />
            <Route path="/about" element={<About />} />
            
            {/* Fallback */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
