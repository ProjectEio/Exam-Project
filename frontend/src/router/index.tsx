import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Spin } from 'antd'
import RoleRoute from './RoleRoute'

const Login = lazy(() => import('@/pages/auth/Login'))
const Register = lazy(() => import('@/pages/auth/Register'))
const Forbidden = lazy(() => import('@/pages/auth/Forbidden'))
const PublicHome = lazy(() => import('@/pages/public/Home'))

const AdminLayout = lazy(() => import('@/layouts/AdminLayout'))
const StudentLayout = lazy(() => import('@/layouts/StudentLayout'))

const Dashboard = lazy(() => import('@/pages/admin/dashboard/Dashboard'))
const UserList = lazy(() => import('@/pages/admin/user/UserList'))
const MajorList = lazy(() => import('@/pages/admin/major/MajorList'))
const CourseList = lazy(() => import('@/pages/admin/course/CourseList'))
const PlanList = lazy(() => import('@/pages/admin/plan/PlanList'))
const RegistrationList = lazy(() => import('@/pages/admin/registration/RegistrationList'))
const ScoreList = lazy(() => import('@/pages/admin/score/ScoreList'))
const Statistics = lazy(() => import('@/pages/admin/statistics/Statistics'))

const StudentHome = lazy(() => import('@/pages/student/home/Home'))
const StudentPlanList = lazy(() => import('@/pages/student/plan/PlanList'))
const StudentMyReg = lazy(() => import('@/pages/student/registration/MyRegistration'))
const StudentMyScore = lazy(() => import('@/pages/student/score/MyScore'))
const StudentProfile = lazy(() => import('@/pages/student/profile/Profile'))

const Loading = (
  <div className="min-h-screen bg-slate-50 flex items-center justify-center px-6">
    <div className="w-full max-w-xl rounded-3xl border border-slate-200 bg-white/90 p-10 shadow-sm text-center">
      <Spin size="large" tip="页面加载中..." />
    </div>
  </div>
)

export default function AppRouter() {
  return (
    <Suspense fallback={Loading}>
      <Routes>
        <Route path="/" element={<PublicHome />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/403" element={<Forbidden />} />

        <Route
          path="/admin"
          element={
            <RoleRoute roles={['ADMIN', 'TEACHER']}>
              <AdminLayout />
            </RoleRoute>
          }
        >
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="users" element={<RoleRoute roles={['ADMIN']}><UserList /></RoleRoute>} />
          <Route path="majors" element={<MajorList />} />
          <Route path="courses" element={<CourseList />} />
          <Route path="plans" element={<PlanList />} />
          <Route path="registrations" element={<RoleRoute roles={['ADMIN']}><RegistrationList /></RoleRoute>} />
          <Route path="scores" element={<ScoreList />} />
          <Route path="statistics" element={<Statistics />} />
        </Route>

        <Route
          path="/student"
          element={
            <RoleRoute roles={['STUDENT']}>
              <StudentLayout />
            </RoleRoute>
          }
        >
          <Route index element={<Navigate to="home" replace />} />
          <Route path="home" element={<StudentHome />} />
          <Route path="plans" element={<StudentPlanList />} />
          <Route path="my-registrations" element={<StudentMyReg />} />
          <Route path="my-scores" element={<StudentMyScore />} />
          <Route path="profile" element={<StudentProfile />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
