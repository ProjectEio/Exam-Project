import { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import useAuthStore from '@/store/auth'
import type { Role } from '@/types'

interface Props {
  roles?: Role[]
  children: ReactNode
}

export default function RoleRoute({ roles, children }: Props) {
  const { token, user } = useAuthStore()
  const location = useLocation()

  if (!token || !user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  if (roles && roles.length && !roles.includes(user.role)) {
    return <Navigate to="/403" replace />
  }
  return <>{children}</>
}
