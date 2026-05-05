import { useState, useEffect, useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Plus, ChevronRight, Pencil, Search, Timer, Database } from 'lucide-react';
import { CreateProblemModal } from './CreateProblemModal';
import { TestCasesPanel } from './TestCasesPanel';
import { EditProblemModal } from './EditProblemModal';
import { getProblem, getProblemsByContest } from '../services/api';
import { ProblemResponse } from '../types/api';
import { toast } from 'sonner';
import { StatusBadge } from '../../components/StatusBadge';

interface ProblemsViewProps {
  contestId: number | null;
}

export function ProblemsView({ contestId }: ProblemsViewProps) {
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [problems, setProblems] = useState<ProblemResponse[]>([]);
  const [selectedProblem, setSelectedProblem] = useState<ProblemResponse | null>(null);
  const [isLoadingProblem, setIsLoadingProblem] = useState(false);
  const [isLoadingList, setIsLoadingList] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [query, setQuery] = useState('');

  const loadProblems = useCallback(async () => {
    if (!contestId) return;
    setIsLoadingList(true);
    try {
      const list = await getProblemsByContest(contestId);
      setProblems(list);
      setSelectedProblem((prev) => {
        if (!prev) return null;
        return list.find((p) => p.id === prev.id) ? prev : null;
      });
    } catch (error) {
      console.error('Failed to load problems list:', error);
      toast.error('Failed to load problems list');
      setProblems([]);
    } finally {
      setIsLoadingList(false);
    }
  }, [contestId]);

  useEffect(() => {
    loadProblems();
  }, [loadProblems]);

  // Load problem details when selected
  const handleProblemSelect = async (problem: ProblemResponse) => {
    setIsLoadingProblem(true);
    try {
      // Fetch fresh problem data from backend
      const problemData = await getProblem(problem.id);
      setSelectedProblem(problemData);
    } catch (error) {
      console.error('Failed to load problem:', error);
      toast.error('Failed to load problem details');
      setSelectedProblem(problem); // Fallback to cached data
    } finally {
      setIsLoadingProblem(false);
    }
  };

  const handleProblemCreated = async (newProblem: ProblemResponse) => {
    // Auto-select newly created problem, then reload list from backend
    setSelectedProblem(newProblem);
    await loadProblems();
  };

  const handleProblemUpdated = async (updatedProblem: ProblemResponse) => {
    setSelectedProblem(updatedProblem);
    await loadProblems();
  };

  if (!contestId) {
    return (
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <CardTitle className="text-2xl text-slate-950">Problems Management</CardTitle>
        </CardHeader>
        <CardContent className="p-6">
          <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-10 text-center">
            <p className="text-sm font-medium text-slate-800">No active or upcoming contest found.</p>
            <p className="mt-1 text-sm text-slate-500">Create or select a contest before adding problems.</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  const filteredProblems = problems.filter((problem) => {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    return (
      String(problem.id).includes(q) ||
      problem.title.toLowerCase().includes(q) ||
      problem.difficulty.toLowerCase().includes(q)
    );
  });

  return (
    <>
      <div className="mb-6 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-blue-700">Contest #{contestId}</p>
            <h1 className="mt-1 text-2xl font-semibold text-slate-950">Problems Management</h1>
            <p className="mt-1 text-sm text-slate-600">Create problem statements and manage public/private judge tests.</p>
          </div>
          <Button className="gap-2 bg-blue-700 hover:bg-blue-800" onClick={() => setCreateModalOpen(true)}>
            <Plus className="h-4 w-4" />
            Create Problem
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="border border-gray-200 shadow-sm lg:col-span-1">
          <CardHeader className="border-b border-slate-200 bg-slate-50">
            <div>
              <CardTitle className="text-lg text-slate-950">Problem List</CardTitle>
              <p className="mt-1 text-sm text-slate-500">{problems.length} problem(s) in this contest.</p>
            </div>
          </CardHeader>
          <CardContent className="p-4">
            <div className="relative mb-4">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search problems..."
                className="h-10 pl-9"
              />
            </div>

            {isLoadingList ? (
              <div className="text-center py-8">
                <p className="text-slate-600 text-sm">Loading problems...</p>
              </div>
            ) : problems.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-slate-600 text-sm mb-2">
                  No problems yet
                </p>
                <p className="text-slate-500 text-xs">
                  Use "Create" to add problems.
                </p>
              </div>
            ) : filteredProblems.length === 0 ? (
              <p className="py-8 text-center text-sm text-slate-500">No problems match your search.</p>
            ) : (
              <div className="space-y-2">
                {filteredProblems.map((problem, index) => (
                  <button
                    key={problem.id}
                    onClick={() => handleProblemSelect(problem)}
                    className={`w-full text-left p-3 rounded-lg border transition-colors ${
                      selectedProblem?.id === problem.id
                        ? 'bg-slate-100 border-slate-300'
                        : 'bg-white border-gray-200 hover:bg-gray-50'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                          Problem {String.fromCharCode(65 + index)}
                        </p>
                        <p className="text-sm font-medium text-slate-900 truncate">
                          {problem.title}
                        </p>
                        <div className="flex items-center gap-2 mt-1">
                          <StatusBadge kind="difficulty" value={problem.difficulty} />
                        </div>
                      </div>
                      <ChevronRight className="w-4 h-4 text-slate-400 flex-shrink-0 ml-2" />
                    </div>
                  </button>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="border border-gray-200 shadow-sm lg:col-span-2">
          <CardHeader className="border-b border-slate-200 bg-slate-50">
            <CardTitle className="text-lg text-slate-950">Problem Details</CardTitle>
          </CardHeader>
          <CardContent className="p-6">
            {isLoadingProblem ? (
              <p className="text-slate-600 py-8 text-center">
                Loading problem details...
              </p>
            ) : !selectedProblem ? (
              <p className="text-slate-600 py-8 text-center">
                Select a problem from the list to view details and manage test cases
              </p>
            ) : (
              <div className="space-y-4">
                <div>
                  <div className="flex items-center justify-between gap-3 mb-2">
                    <h3 className="text-2xl font-semibold text-slate-950">{selectedProblem.title}</h3>
                    <Button
                      size="sm"
                      variant="outline"
                      className="gap-2"
                      onClick={() => setEditModalOpen(true)}
                    >
                      <Pencil className="w-4 h-4" />
                      Edit Problem
                    </Button>
                  </div>
                  <div className="flex flex-wrap items-center gap-2 mb-4">
                    <StatusBadge kind="difficulty" value={selectedProblem.difficulty} />
                    <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-600">
                      <Timer className="h-3.5 w-3.5" />
                      {selectedProblem.timeLimit} ms
                    </span>
                    <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-600">
                      <Database className="h-3.5 w-3.5" />
                      {selectedProblem.memoryLimit} MB
                    </span>
                  </div>
                </div>

                <div>
                  <h4 className="font-semibold text-slate-800 mb-2">Problem Statement</h4>
                  <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
                    <p className="text-sm leading-6 text-slate-700 whitespace-pre-wrap">
                      {selectedProblem.description}
                    </p>
                  </div>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Test Cases Panel - Only shown when a problem is selected */}
      {selectedProblem && (
        <TestCasesPanel 
          problemId={selectedProblem.id} 
          problemTitle={selectedProblem.title}
        />
      )}

      {selectedProblem && (
        <EditProblemModal
          open={editModalOpen}
          onOpenChange={setEditModalOpen}
          problem={selectedProblem}
          onSuccess={handleProblemUpdated}
        />
      )}

      <CreateProblemModal 
        open={createModalOpen}
        onOpenChange={setCreateModalOpen}
        contestId={contestId}
        onSuccess={handleProblemCreated}
      />
    </>
  );
}
